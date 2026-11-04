package com.mario.buonipasto.ai;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.Articolo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lettura della dieta in due passaggi, per avere quantita' giuste:
 *  1. l'AI ESTRAE ogni alimento di ogni pasto, giorno e persona (senza sommare nulla);
 *  2. l'app SOMMA le quantita' per alimento, le riporta ai giorni scelti e alle persone, e le arrotonda.
 * Prima l'AI faceva anche i conti e tendeva a riassumere: alimenti mancanti e quantita' sbagliate.
 */
public final class DietaLettore {
    private static final String[] REPARTI = {"frutta_verdura", "pane_cereali", "carne_pesce", "latticini_uova",
            "dispensa", "surgelati", "bevande", "altro"};

    private DietaLettore() {}

    /** Una riga estratta dalla dieta. */
    static final class Voce {
        String giorno, persona, nome, unita, reparto;
        double quantita;
    }

    // ------------------------------------------------------------------ istruzioni

    static String istruzioni() {
        String lingua = Lingua.INSTANCE.inglese() ? "in inglese" : "in italiano";
        return "Sei un estrattore di dati. Ricevi una dieta e devi elencare TUTTI gli alimenti, riga per riga, "
                + "SENZA riassumere, SENZA sommare e SENZA saltare nulla (anche caffè, olio, cioccolato, spuntini, pre-nanna).\n\n"
                + "Per ogni giorno, ogni pasto, ogni alimento e ogni persona crea UNA voce.\n"
                + "REGOLE:\n"
                + "1. Se la dieta indica quantità per più persone (es. \"A 60 g · B 70 g\"), crea una voce per A e una per B. "
                + "Se la dieta è per una persona sola usa persona \"A\". Ignora le quantità 0.\n"
                + "2. Se una quantità contiene più alimenti uniti da \"+\" (es. \"B 150 ml milk + 35 g rusks + 10 g jam\"), "
                + "crea una voce per ciascun alimento.\n"
                + "3. Se una riga propone alternative (\"X or Y\", \"X oppure Y\") e la quantità dice quale alimento usa ogni persona "
                + "(es. \"A 125 g yogurt · B 200 ml milk\"), usa per ciascuno l'alimento indicato. Se non è indicato usa la prima alternativa.\n"
                + "4. Il simbolo ↔ indica una sostituzione: conta solo le quantità numeriche delle persone.\n"
                + "5. \"q\" è un numero, \"u\" è \"g\", \"ml\" oppure \"pz\". Converti: latte e bevande in ml; uova intere in pz "
                + "(50 g di uova = 1 pz); albumi in g; 1 vasetto di yogurt = 125 g; 1 cucchiaio di olio = 10 g; "
                + "1 tazzina di caffè = 7 g di caffè macinato (la quantità \"40 g\" di caffè moka è caffè preparato: usa 7 g). "
                + "Se la quantità non è indicata metti q = 0.\n"
                + "6. \"n\" è il nome generico del prodotto da comprare, " + lingua + ", con la maiuscola iniziale, SEMPRE UGUALE "
                + "per lo stesso prodotto in tutta la dieta (es. " + (Lingua.INSTANCE.inglese()
                        ? "\"Chicken breast\", \"Oat flakes\", \"Low-fat yogurt\", \"Skim milk\", \"Vegetables\", \"Fresh fruit\", "
                        + "\"Bread\", \"Dried legumes\", \"Cooked legumes\""
                        : "\"Petto di pollo\", \"Fiocchi d'avena\", \"Yogurt magro\", \"Latte scremato\", \"Verdure\", "
                        + "\"Frutta fresca\", \"Pane\", \"Legumi secchi\", \"Legumi lessati\"") + "). "
                + "Usa SOLO la lingua indicata, anche se la dieta è scritta in un'altra lingua.\n"
                + "7. \"r\" è il reparto, uno tra: frutta_verdura, pane_cereali, carne_pesce, latticini_uova, dispensa, surgelati, "
                + "bevande, altro.\n"
                + "8. \"g\" è il giorno (es. \"Lunedì\") e \"p\" la persona (\"A\", \"B\", ...).\n\n"
                + "Rispondi SOLO con JSON valido, senza altro testo:\n"
                + "{\"voci\":[{\"g\":\"Lunedì\",\"p\":\"A\",\"n\":\"Fiocchi d'avena\",\"q\":60,\"u\":\"g\",\"r\":\"pane_cereali\"}]}";
    }

    // ------------------------------------------------------------------ lettura (chiamata da Dieta.leggi)

    public static List<Articolo> leggi(Context ctx, Uri uri, String nomeFile, int giorni, String chiave, String chiaveGroq)
            throws Exception {
        Context lc = Lingua.INSTANCE.contesto(ctx);
        byte[] pdf = DueDiete.leggi(ctx, uri);
        String istr = istruzioni();
        String richiesta = "Estrai tutte le voci di questa dieta.";
        List<Voce> voci = new ArrayList<Voce>();

        final String pdf64 = Base64.encodeToString(pdf, Base64.NO_WRAP);
        final Gemini.Messaggi msg = Motore.INSTANCE.messaggi(lc);
        EstrattoreDieta ed = new EstrattoreDieta(chiave, Motore.INSTANCE.modelli(ctx), msg);
        try {
            voci.addAll(ed.estrai(pdf64));
        } catch (Gemini.Errore eg) {
            if (eg.tipo == Gemini.Errore.CHIAVE || eg.tipo == Gemini.Errore.ANNULLATA) throw eg;
            // riserva Groq: legge le pagine come immagini, 3 alla volta (il suo limite), e unisce i risultati
            Groq q = new Groq(new Groq.Http(), chiaveGroq);
            q.messaggi = msg;
            q.erroreChiave = Ui.s(lc, "ai_err_chiave_groq");
            voci.clear();
            try {
                List<String> pagine = DueDiete.pagine(ctx, pdf, 12);
                for (int i = 0; i < pagine.size(); i += 3) {
                    JsonArray parti = new JsonArray();
                    for (int j = i; j < Math.min(i + 3, pagine.size()); j++) {
                        JsonObject inline = new JsonObject();
                        inline.addProperty("mimeType", "image/jpeg");
                        inline.addProperty("data", pagine.get(j));
                        JsonObject pf = new JsonObject();
                        pf.add("inlineData", inline);
                        parti.add(pf);
                    }
                    JsonObject pt = new JsonObject();
                    pt.addProperty("text", richiesta + " (pagine " + (i + 1) + "-" + Math.min(i + 3, pagine.size())
                            + " di " + pagine.size() + ")");
                    parti.add(pt);
                    voci.addAll(analizzaVoci(q.conversa(istr, conversazione(parti), null, null, Groq.FOTO)));
                }
            } finally {
                UsoAi.aggiungi(ctx, 0, q.richieste);
            }
        } finally {
            UsoAi.aggiungi(ctx, ed.richieste, 0);
        }
        return lista(voci, giorni, PersoneDieta.persone, nomeFile, Lingua.INSTANCE.inglese());
    }

    private static Gemini nuovoGemini(String chiave, String[] modelli, Gemini.Messaggi msg) {
        Gemini g = new Gemini(new Gemini.Http(), chiave);
        g.usaModelli(modelli);
        g.messaggi = msg;
        g.rispostaJson = true;
        return g;
    }

    private static JsonArray partiPdf(String pdf64, String testo) {
        JsonArray parti = new JsonArray();
        JsonObject inline = new JsonObject();
        inline.addProperty("mimeType", "application/pdf");
        inline.addProperty("data", pdf64);
        JsonObject pf = new JsonObject();
        pf.add("inlineData", inline);
        parti.add(pf);
        JsonObject pt = new JsonObject();
        pt.addProperty("text", testo);
        parti.add(pt);
        return parti;
    }

    static String istruzioniGiorni() {
        return "Leggi la dieta ed elenca i nomi dei giorni che contiene, nell'ordine e scritti esattamente come nel documento "
                + "(es. \"Monday\" o \"Lunedì\"). Se la dieta è un unico schema giornaliero senza giorni, rispondi con una lista vuota. "
                + "Non contare sezioni come \"Main Differences\" o note finali. Rispondi SOLO con JSON: {\"giorni\":[\"...\"]}";
    }

    static List<String> elencoGiorni(String testo) {
        List<String> out = new ArrayList<String>();
        if (testo == null) return out;
        int a = testo.indexOf('{'), b = testo.lastIndexOf('}');
        if (a < 0 || b <= a) return out;
        try {
            JsonObject o = JsonParser.parseString(testo.substring(a, b + 1)).getAsJsonObject();
            if (o.has("giorni") && o.get("giorni").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("giorni")) {
                    String g = e.isJsonNull() ? "" : e.getAsString().trim();
                    if (!g.isEmpty() && !out.contains(g) && out.size() < 14) out.add(g);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static JsonArray conversazione(JsonArray parti) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.add("parts", parti);
        JsonArray a = new JsonArray();
        a.add(m);
        return a;
    }

    // ------------------------------------------------------------------ analisi e somma (pure, testate)

    static List<Voce> analizzaVoci(String testo) {
        List<Voce> out = new ArrayList<Voce>();
        if (testo == null) return out;
        int a = testo.indexOf('{'), b = testo.lastIndexOf('}');
        if (a < 0 || b <= a) return out;
        JsonObject json;
        try {
            json = JsonParser.parseString(testo.substring(a, b + 1)).getAsJsonObject();
        } catch (RuntimeException e) {
            return out;
        }
        JsonArray arr = json.has("voci") && json.get("voci").isJsonArray() ? json.getAsJsonArray("voci") : null;
        if (arr == null) return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            Voce v = new Voce();
            v.nome = str(o, "n");
            if (v.nome.isEmpty()) continue;
            v.giorno = str(o, "g");
            v.persona = str(o, "p");
            v.unita = str(o, "u").toLowerCase(Locale.ROOT);
            v.reparto = str(o, "r").toLowerCase(Locale.ROOT);
            try {
                v.quantita = o.has("q") && !o.get("q").isJsonNull() ? Double.parseDouble(o.get("q").getAsString().replace(',', '.')) : 0;
            } catch (RuntimeException e) {
                v.quantita = 0;
            }
            if (v.quantita < 0) v.quantita = 0;
            out.add(v);
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : "";
    }

    private static String unitaNormale(String u) {
        if (u.equals("ml") || u.equals("l") || u.startsWith("lit")) return "ml";
        if (u.equals("pz") || u.startsWith("pc") || u.startsWith("pez") || u.equals("n") || u.startsWith("unit")) return "pz";
        return "g";
    }

    /** Somma per prodotto e unita', riporta ai giorni e alle persone richiesti, arrotonda. */
    static List<Articolo> lista(List<Voce> voci, int giorni, int personeScelte, String fonte, boolean inglese) {
        // giorni e persone presenti nella dieta
        Set<String> giorniDieta = new HashSet<String>(), personeDieta = new HashSet<String>();
        for (Voce v : voci) {
            if (!v.giorno.isEmpty()) giorniDieta.add(v.giorno.toLowerCase(Locale.ROOT));
            if (!v.persona.isEmpty()) personeDieta.add(v.persona.toUpperCase(Locale.ROOT));
        }
        int nPersone = Math.max(1, personeDieta.size());
        // se la dieta e' gia' per due persone (A e B) non si raddoppia di nuovo
        double fattore = Math.max(1.0, (double) personeScelte / nPersone);

        // giorni della settimana scelti: se i giorni della dieta si riconoscono (lunedi', Monday, Giorno 3...)
        // si prendono proprio quelli; un giorno scelto che la dieta non ha vale come un giorno medio.
        int mask = GiorniDieta.maschera;
        int nScelti = Integer.bitCount(mask);
        if (nScelti == 0) {
            mask = 0x7F;
            nScelti = 7;
        }
        Map<String, Integer> indici = new HashMap<String, Integer>();
        boolean riconosciuti = giorniDieta.size() >= 2;
        int presenti = 0;
        for (String g : giorniDieta) {
            int i = GiorniDieta.indice(g);
            if (i < 0 || (presenti & (1 << i)) != 0) {
                riconosciuti = false;
                break;
            }
            presenti |= 1 << i;
            indici.put(g, i);
        }
        // vecchio criterio (piano di un solo giorno da ripetere, o giorni senza nome): media giornaliera
        int nGiorni = giorniDieta.size() <= 1 ? 1 : Math.max(7, giorniDieta.size());
        double pesoMedio = (double) nScelti / nGiorni;
        double extra = riconosciuti ? (double) Integer.bitCount(mask & ~presenti) / giorniDieta.size() : 0;

        Map<String, String> nomi = new LinkedHashMap<String, String>();
        Map<String, double[]> somme = new LinkedHashMap<String, double[]>(); // g, ml, pz
        Map<String, Map<String, Integer>> reparti = new LinkedHashMap<String, Map<String, Integer>>();
        Set<String> usati = new HashSet<String>();
        for (Voce v : voci) {
            String k = v.nome.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!nomi.containsKey(k)) {
                nomi.put(k, Character.toUpperCase(v.nome.charAt(0)) + v.nome.substring(1));
                somme.put(k, new double[3]);
                reparti.put(k, new LinkedHashMap<String, Integer>());
            }
            String u = unitaNormale(v.unita);
            double q = v.quantita;
            String gk = v.giorno.toLowerCase(Locale.ROOT);
            double peso = riconosciuti && indici.containsKey(gk)
                    ? ((mask & (1 << indici.get(gk))) != 0 ? 1 : 0) + extra : pesoMedio;
            if (peso <= 0) continue; // alimento di un giorno non scelto
            q *= peso;
            usati.add(k);
            if (v.unita.equals("l")) q *= 1000;
            if (v.unita.equals("kg")) q *= 1000;
            somme.get(k)[u.equals("g") ? 0 : u.equals("ml") ? 1 : 2] += q;
            String r = v.reparto;
            boolean valido = false;
            for (String x : REPARTI) if (x.equals(r)) valido = true;
            if (!valido) r = "altro";
            Map<String, Integer> c = reparti.get(k);
            c.put(r, (c.containsKey(r) ? c.get(r) : 0) + 1);
        }

        List<Articolo> out = new ArrayList<Articolo>();
        for (String k : nomi.keySet()) {
            if (!usati.contains(k)) continue;
            double[] s = somme.get(k);
            List<String> parti = new ArrayList<String>();
            if (s[0] > 0) parti.add(peso(s[0] * fattore, "g", "kg", inglese));
            if (s[1] > 0) parti.add(peso(s[1] * fattore, "ml", "l", inglese));
            int pezzi = (int) Math.ceil(s[2] * fattore - 0.01);
            if (pezzi > 0) parti.add(pezzi + (inglese ? " pcs" : " pz"));
            if (parti.isEmpty() && s[0] + s[1] + s[2] > 0) continue;
            String reparto = "altro";
            int max = -1;
            for (Map.Entry<String, Integer> e : reparti.get(k).entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    reparto = e.getKey();
                }
            }
            out.add(new Articolo(UUID.randomUUID().toString(), nomi.get(k), join(parti), reparto, false, fonte));
        }
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String x : l) {
            if (b.length() > 0) b.append(" + ");
            b.append(x);
        }
        return b.toString();
    }

    /** 390 g · 1,4 kg · 2 kg (in inglese con il punto decimale). */
    static String peso(double v, String piccola, String grande, boolean inglese) {
        if (v < 1000) {
            long arrot = v < 100 ? Math.round(v / 5.0) * 5 : Math.round(v / 10.0) * 10;
            return Math.max(5, arrot) + " " + piccola;
        }
        double k = Math.round(v / 100.0) / 10.0;
        String t = (k == Math.rint(k)) ? String.valueOf((long) k) : String.valueOf(k);
        if (!inglese) t = t.replace('.', ',');
        return t + " " + grande;
    }
}
