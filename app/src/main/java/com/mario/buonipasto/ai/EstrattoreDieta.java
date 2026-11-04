package com.mario.buonipasto.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mario.buonipasto.Lingua;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Estrazione della dieta con Gemini, pensata per risultati stabili:
 *  1. CATALOGO: un elenco unico dei prodotti della dieta (nome, unita', reparto) e dei giorni;
 *  2. un giorno per richiesta, con risposta vincolata da uno schema JSON in cui il nome del prodotto
 *     puo' essere SOLO uno di quelli del catalogo (niente "Verdure" e "Verdure di fibra" separate);
 *  3. temperatura 0: a parita' di PDF il risultato non cambia da una volta all'altra.
 */
final class EstrattoreDieta {
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] REPARTI = {"frutta_verdura", "pane_cereali", "carne_pesce", "latticini_uova",
            "dispensa", "surgelati", "bevande", "altro"};

    private final String chiave;
    private final String[] modelli;
    private final Gemini.Messaggi msg;
    private volatile boolean pensiero = true;
    int richieste;

    EstrattoreDieta(String chiave, String[] modelli, Gemini.Messaggi msg) {
        this.chiave = chiave;
        this.modelli = modelli;
        this.msg = msg;
    }

    /** Prodotto del catalogo. */
    static final class Prodotto {
        String nome, unita, reparto;
    }

    // ------------------------------------------------------------------ chiamata a Gemini

    String chiama(String istruzioni, String pdf64, String testo, JsonObject schema) throws Gemini.Errore {
        int modello = 0, tentativi = 0;
        while (true) {
            JsonObject corpo = new JsonObject();
            JsonObject sys = new JsonObject();
            JsonArray sp = new JsonArray();
            JsonObject st = new JsonObject();
            st.addProperty("text", istruzioni);
            sp.add(st);
            sys.add("parts", sp);
            corpo.add("systemInstruction", sys);
            JsonArray parti = new JsonArray();
            JsonObject inline = new JsonObject();
            inline.addProperty("mimeType", "application/pdf");
            inline.addProperty("data", pdf64);
            JsonObject pf = new JsonObject();
            pf.add("inlineData", inline);
            if (pdf64 != null) parti.add(pf);
            JsonObject pt = new JsonObject();
            pt.addProperty("text", testo);
            parti.add(pt);
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.add("parts", parti);
            JsonArray contents = new JsonArray();
            contents.add(m);
            corpo.add("contents", contents);
            JsonObject gen = new JsonObject();
            gen.addProperty("temperature", 0);
            gen.addProperty("responseMimeType", "application/json");
            gen.add("responseSchema", schema);
            gen.addProperty("maxOutputTokens", 16384);
            if (pensiero) {
                JsonObject th = new JsonObject();
                th.addProperty("thinkingLevel", "low");
                gen.add("thinkingConfig", th);
            }
            corpo.add("generationConfig", gen);

            int codice;
            String risposta;
            try {
                synchronized (this) {
                    richieste++;
                }
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + modelli[modello] + ":generateContent").openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(90000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("x-goog-api-key", chiave);
                OutputStream os = c.getOutputStream();
                os.write(corpo.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                codice = c.getResponseCode();
                InputStream is = codice >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                if (is != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
                    is.close();
                }
                c.disconnect();
                risposta = new String(b.toByteArray(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                if (++tentativi <= 2) continue;
                throw new Gemini.Errore(Gemini.Errore.RETE, msg.rete);
            }
            String errore = "";
            try {
                errore = JsonParser.parseString(risposta).getAsJsonObject().getAsJsonObject("error").get("message").getAsString();
            } catch (RuntimeException ignored) {
            }
            String e = errore.toLowerCase(Locale.ROOT);
            if (codice == 400 && pensiero && e.contains("thinking")) {
                pensiero = false;
                continue;
            }
            if ((codice == 404 || codice == 429) && modello < modelli.length - 1) {
                modello++;
                continue;
            }
            if (codice >= 500 && ++tentativi <= 3) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                if (tentativi >= 2 && modello < modelli.length - 1) modello++;
                continue;
            }
            if (codice == 401 || codice == 403 || e.contains("api key")) throw new Gemini.Errore(Gemini.Errore.CHIAVE, msg.chiave);
            if (codice == 429) throw new Gemini.Errore(Gemini.Errore.QUOTA, msg.quota);
            if (codice != 200) throw new Gemini.Errore(Gemini.Errore.ALTRO, msg.errore + " (" + codice + ")" + (errore.isEmpty() ? "" : ": " + errore));
            StringBuilder t = new StringBuilder();
            try {
                JsonObject cand = JsonParser.parseString(risposta).getAsJsonObject().getAsJsonArray("candidates").get(0).getAsJsonObject();
                for (JsonElement p : cand.getAsJsonObject("content").getAsJsonArray("parts")) {
                    JsonObject po = p.getAsJsonObject();
                    if (po.has("text") && !(po.has("thought") && po.get("thought").getAsBoolean())) t.append(po.get("text").getAsString());
                }
            } catch (RuntimeException ex) {
                throw new Gemini.Errore(Gemini.Errore.ALTRO, msg.vuota);
            }
            return t.toString();
        }
    }

    // ------------------------------------------------------------------ schemi

    static JsonObject tipo(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("type", t);
        return o;
    }

    static JsonObject enumStringa(List<String> valori) {
        JsonObject o = tipo("STRING");
        JsonArray a = new JsonArray();
        for (String v : valori) a.add(v);
        o.add("enum", a);
        return o;
    }

    static JsonObject oggetto(JsonObject proprieta, String... obbligatori) {
        JsonObject o = tipo("OBJECT");
        o.add("properties", proprieta);
        JsonArray r = new JsonArray();
        for (String x : obbligatori) r.add(x);
        o.add("required", r);
        return o;
    }

    static JsonObject array(JsonObject items) {
        JsonObject o = tipo("ARRAY");
        o.add("items", items);
        return o;
    }

    static List<String> lista(String... v) {
        List<String> l = new ArrayList<String>();
        for (String x : v) l.add(x);
        return l;
    }

    // ------------------------------------------------------------------ passo 1: catalogo e giorni

    private static String istruzioniCatalogo() {
        boolean en = Lingua.INSTANCE.inglese();
        return "Leggi tutta la dieta. Restituisci:\n"
                + "- \"giorni\": i nomi dei giorni nell'ordine e scritti come nel documento (lista vuota se è un unico schema giornaliero). "
                + "Non contare note o sezioni finali come \"Main Differences\".\n"
                + "- \"prodotti\": TUTTI i prodotti da comprare citati nella dieta, UNO per prodotto, compresi caffè, olio, "
                + "cioccolato, spezie, passata, ingredienti di pancake e di spuntini/pre-nanna. Unifica sinonimi e varianti dello stesso "
                + "prodotto (es. \"Fiber vegetables\" e \"Vegetables\" sono un solo prodotto; \"olio\" e \"olio extravergine\" uno solo; "
                + "\"uova\" e \"uova intere\" uno solo). Tieni separati prodotti diversi (es. albumi e uova, yogurt greco e yogurt magro, "
                + "legumi secchi e legumi lessati, piselli e legumi). Lo yogurt greco è SEMPRE un prodotto a sé (\"Yogurt greco\"/\"Greek yogurt\"), "
                + "mai unito a fiocchi di latte, cottage cheese o altri formaggi. "
                + "Carni e pesci diversi restano prodotti diversi: pollo, tacchino, vitello (veal), manzo, maiale, hamburger, pesce, "
                + "tonno, gamberi ecc. (il vitello NON è pollo).\n"
                + "- Leggi OGNI ingrediente dei piatti composti: \"A 100 g rice + 50 g eggs + 160 g peas + 30 g ham\" contiene riso, uova, "
                + "piselli e prosciutto, e tutti e quattro vanno nel catalogo. Prima di rispondere ricontrolla pranzi e cene riga per riga.\n"
                + "  \"n\" = nome generico da supermercato " + (en ? "in inglese" : "in italiano") + " con la maiuscola iniziale; "
                + "\"u\" = unità in cui contarlo: \"g\" per i cibi a peso, \"ml\" per latte e bevande, \"pz\" SOLO per uova intere "
                + "e per cose che la dieta conta a pezzi senza grammi (se la dieta dà i grammi, es. \"50 g croissant\", usa \"g\"); \"r\" = reparto.";
    }

    private static JsonObject schemaCatalogo() {
        JsonObject prodProps = new JsonObject();
        prodProps.add("n", tipo("STRING"));
        prodProps.add("u", enumStringa(lista("g", "ml", "pz")));
        prodProps.add("r", enumStringa(lista(REPARTI)));
        JsonObject props = new JsonObject();
        props.add("giorni", array(tipo("STRING")));
        props.add("prodotti", array(oggetto(prodProps, "n", "u", "r")));
        return oggetto(props, "giorni", "prodotti");
    }

    // ------------------------------------------------------------------ passo 2: voci di un giorno

    private static String istruzioniGiorno(List<Prodotto> catalogo) {
        StringBuilder c = new StringBuilder();
        for (Prodotto p : catalogo) c.append("- ").append(p.nome).append(" (").append(p.unita).append(")\n");
        return "Sei un estrattore di dati. Elenca TUTTI gli alimenti del giorno richiesto, pasto per pasto e persona per persona, "
                + "SENZA riassumere, SENZA sommare e SENZA saltare nulla (anche caffè, olio, cioccolato, spuntini, pre-nanna).\n"
                + "REGOLE:\n"
                + "1. \"A 60 g · B 70 g\" = una voce per A e una per B. Dieta per una persona sola: persona \"A\". Ignora le quantità 0.\n"
                + "2. \"B 150 ml milk + 35 g rusks + 10 g jam\" = una voce per ogni alimento.\n"
                + "3. \"X or Y\" con quantità che dicono cosa usa ciascuno (es. \"A 125 g yogurt · B 200 ml milk\"): per ogni persona "
                + "usa SOLO l'alimento indicato, mai entrambi. Se non è indicato usa la prima alternativa.\n"
                + "   Esempio: \"Fresh fruit (A 150 g · B 0 g) or Greek yogurt with fruit (A 0 g ↔ B 150 g)\" = A frutta 150 g, "
                + "B yogurt greco 150 g.\n"
                + "4. ↔ indica una sostituzione: conta solo le quantità numeriche.\n"
                + "4a. Ogni carne va nel SUO prodotto: \"veal\"/vitello non è mai \"chicken\"/pollo; se manca nel catalogo usa \"nuovi\".\n"
                + "4b. Yogurt greco (Greek yogurt) va SEMPRE nel prodotto yogurt greco del catalogo (o in uno yogurt), MAI in fiocchi di latte, "
                + "cottage cheese o formaggi. \"Fruit yogurt\" e \"yogurt with fruit\" sono yogurt, NON frutta fresca: la frutta fresca si conta "
                + "solo dove ha i suoi grammi. Ogni ingrediente dei piatti composti (\"+ 160 g peas\") è una voce: non saltarne nessuno.\n"
                + "5. Usa per \"n\" SOLO un nome del catalogo e per \"u\" l'unità del catalogo. Conversioni: uova intere 50 g = 1 pz; "
                + "1 vasetto di yogurt = 125 g; 1 cucchiaio di olio = 10 g; caffè moka \"40 g\" o 1 tazzina = 7 g di caffè macinato; "
                + "latte in g = ml. Quantità non indicata = 0.\n\n"
                + "6. Se un alimento del giorno NON ha un prodotto adatto nel catalogo, NON forzarlo in un prodotto diverso e NON saltarlo: "
                + "mettilo in \"nuovi\" con nome generico da supermercato, quantità, unità e reparto. Usa \"nuovi\" solo in questo caso.\n\n"
                + "CATALOGO (nome e unità):\n" + c;
    }

    private static JsonObject schemaGiorno(List<Prodotto> catalogo) {
        List<String> nomi = new ArrayList<String>();
        for (Prodotto p : catalogo) nomi.add(p.nome);
        JsonObject vProps = new JsonObject();
        vProps.add("p", tipo("STRING"));
        vProps.add("n", enumStringa(nomi));
        vProps.add("q", tipo("NUMBER"));
        vProps.add("u", enumStringa(lista("g", "ml", "pz")));
        JsonObject props = new JsonObject();
        props.add("voci", array(oggetto(vProps, "p", "n", "q", "u")));
        JsonObject nProps = new JsonObject();
        nProps.add("p", tipo("STRING"));
        nProps.add("n", tipo("STRING"));
        nProps.add("q", tipo("NUMBER"));
        nProps.add("u", enumStringa(lista("g", "ml", "pz")));
        nProps.add("r", enumStringa(lista(REPARTI)));
        props.add("nuovi", array(oggetto(nProps, "p", "n", "q", "u", "r")));
        return oggetto(props, "voci");
    }

    // ------------------------------------------------------------------ insieme

    List<DietaLettore.Voce> estrai(final String pdf64) throws Exception {
        // 1) catalogo e giorni
        JsonObject cat = JsonParser.parseString(chiama(istruzioniCatalogo(), pdf64, "Catalogo e giorni di questa dieta.",
                schemaCatalogo())).getAsJsonObject();
        final List<Prodotto> catalogo = new ArrayList<Prodotto>();
        final Map<String, Prodotto> perNome = new LinkedHashMap<String, Prodotto>();
        for (JsonElement el : cat.getAsJsonArray("prodotti")) {
            JsonObject o = el.getAsJsonObject();
            Prodotto p = new Prodotto();
            p.nome = o.get("n").getAsString().trim();
            p.unita = o.get("u").getAsString();
            p.reparto = o.get("r").getAsString();
            if (p.nome.isEmpty() || perNome.containsKey(p.nome)) continue;
            perNome.put(p.nome, p);
            catalogo.add(p);
        }
        if (catalogo.isEmpty()) return new ArrayList<DietaLettore.Voce>();
        List<String> giorni = new ArrayList<String>();
        for (JsonElement el : cat.getAsJsonArray("giorni")) {
            String g = el.getAsString().trim();
            if (!g.isEmpty() && !giorni.contains(g) && giorni.size() < 14) giorni.add(g);
        }
        if (giorni.isEmpty()) giorni.add(""); // schema giornaliero

        // 2) un giorno per richiesta, 4 in parallelo
        final String istr = istruzioniGiorno(catalogo);
        final JsonObject schema = schemaGiorno(catalogo);
        ExecutorService es = Executors.newFixedThreadPool(4);
        List<Future<List<DietaLettore.Voce>>> fut = new ArrayList<Future<List<DietaLettore.Voce>>>();
        for (final String giorno : giorni) {
            fut.add(es.submit(new Callable<List<DietaLettore.Voce>>() {
                @Override
                public List<DietaLettore.Voce> call() throws Exception {
                    String richiesta = giorno.isEmpty() ? "Estrai tutte le voci della dieta (schema di un giorno)."
                            : "Estrai TUTTE le voci SOLO del giorno \"" + giorno + "\" (tutti i pasti, tutte le persone). Ignora gli altri giorni.";
                    JsonObject r = JsonParser.parseString(chiama(istr, pdf64, richiesta, schema)).getAsJsonObject();
                    List<DietaLettore.Voce> l = new ArrayList<DietaLettore.Voce>();
                    for (JsonElement el : r.getAsJsonArray("voci")) {
                        JsonObject o = el.getAsJsonObject();
                        DietaLettore.Voce v = new DietaLettore.Voce();
                        v.giorno = giorno;
                        v.persona = o.has("p") ? o.get("p").getAsString().trim() : "";
                        v.nome = o.get("n").getAsString().trim();
                        v.quantita = Math.max(0, o.get("q").getAsDouble());
                        v.unita = o.get("u").getAsString();
                        Prodotto p = perNome.get(v.nome);
                        v.reparto = p != null ? p.reparto : "altro";
                        if (p != null) adatta(v, p);
                        correggi(v);
                        l.add(v);
                    }
                    // alimenti sfuggiti al catalogo (es. piselli dentro un piatto composto)
                    if (r.has("nuovi") && r.get("nuovi").isJsonArray()) {
                        for (JsonElement el : r.getAsJsonArray("nuovi")) {
                            JsonObject o = el.getAsJsonObject();
                            if (!o.has("n") || !o.has("q")) continue;
                            DietaLettore.Voce v = new DietaLettore.Voce();
                            v.giorno = giorno;
                            v.persona = o.has("p") ? o.get("p").getAsString().trim() : "";
                            v.nome = o.get("n").getAsString().trim();
                            v.quantita = Math.max(0, o.get("q").getAsDouble());
                            v.unita = o.has("u") ? o.get("u").getAsString() : "g";
                            v.reparto = o.has("r") ? o.get("r").getAsString() : "altro";
                            if (v.nome.isEmpty() || v.quantita <= 0) continue;
                            Prodotto p = simile(v.nome, catalogo);
                            if (p != null) {
                                v.nome = p.nome;
                                v.reparto = p.reparto;
                                adatta(v, p);
                            }
                            correggi(v);
                            l.add(v);
                        }
                    }
                    return l;
                }
            }));
        }
        es.shutdown();
        List<DietaLettore.Voce> tutte = new ArrayList<DietaLettore.Voce>();
        try {
            for (Future<List<DietaLettore.Voce>> f : fut) tutte.addAll(f.get());
        } catch (ExecutionException e) {
            for (Future<List<DietaLettore.Voce>> f : fut) f.cancel(true);
            if (e.getCause() instanceof Gemini.Errore) throw (Gemini.Errore) e.getCause();
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
        return tutte;
    }

    /**
     * Correzioni sicure fatte dall'app, perche' il modello non sempre le applica:
     * uova intere sempre a pezzi (50 g = 1 uovo), caffe' moka "40 g" = una tazzina = 7 g di macinato.
     */
    static void correggi(DietaLettore.Voce v) {
        String n = v.nome.toLowerCase(Locale.ROOT);
        boolean uova = (n.contains("egg") || n.contains("uov")) && !n.contains("white") && !n.contains("album");
        if (uova && v.unita.equals("g")) {
            v.quantita = v.quantita / 50.0;
            v.unita = "pz";
        }
        boolean caffe = (n.contains("coffee") || n.contains("caff")) && !n.contains("milk") && !n.contains("latte");
        if (caffe && v.unita.equals("g") && v.quantita >= 20) v.quantita = 7;
        // "50 pz" di croissant e simili: erano grammi
        if (!uova && v.unita.equals("pz") && v.quantita > 12) v.unita = "g";
    }

    /** Prodotto del catalogo con lo stesso nome (o un nome che contiene l'altro), altrimenti null. */
    static Prodotto simile(String nome, List<Prodotto> catalogo) {
        String n = nome.toLowerCase(Locale.ROOT).trim();
        for (Prodotto p : catalogo) if (p.nome.toLowerCase(Locale.ROOT).equals(n)) return p;
        for (Prodotto p : catalogo) {
            String c = p.nome.toLowerCase(Locale.ROOT);
            if (c.length() >= 4 && n.length() >= 4 && (c.contains(n) || n.contains(c))) return p;
        }
        return null;
    }

    /** Porta la voce all'unita' del catalogo (uova da g a pz, ml e g equivalenti). */
    static void adatta(DietaLettore.Voce v, Prodotto p) {
        if (v.unita.equals(p.unita)) return;
        if (p.unita.equals("pz") && v.unita.equals("g")) {
            v.quantita = v.quantita / 50.0; // uova e simili: 50 g a pezzo
        } else if (p.unita.equals("g") && v.unita.equals("pz")) {
            v.quantita = v.quantita * 50.0;
        }
        // g e ml: per latte, yogurt, passata e simili si considerano equivalenti
        v.unita = p.unita;
    }
}
