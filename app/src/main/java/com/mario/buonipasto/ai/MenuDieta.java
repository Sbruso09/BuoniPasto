package com.mario.buonipasto.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mario.buonipasto.Lingua;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Scheda "Menu": legge il PDF della dieta e salva, giorno per giorno, tutti i pasti con alimenti e quantita'
 * (per persona, se la dieta e' per due). La schermata mostra in automatico il giorno di oggi.
 */
public final class MenuDieta {
    private static final String PREFS = "menu_dieta";

    static volatile boolean inCorso;
    static volatile String fase = "";
    static volatile String errore = "";
    private static final List<Runnable> ascolti = new ArrayList<Runnable>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private MenuDieta() {}

    // ------------------------------------------------------------------ scheda nella barra in basso

    /** Posizione nella barra -> scheda: Buoni, Lista, Menu, Assistente, Impostazioni. */
    public static int tab(int posizione) {
        int[] m = {0, 1, 4, 2, 3};
        return posizione >= 0 && posizione < m.length ? m[posizione] : posizione;
    }

    /** Crea la schermata nativa per la scheda Menu (usata da AndroidView). */
    public static final class Fabbrica implements kotlin.jvm.functions.Function1<Object, Object> {
        @Override
        public Object invoke(Object c) {
            return new MenuView((Context) c);
        }
    }

    // ------------------------------------------------------------------ dati salvati

    private static JsonObject leggiJson(Context c, String k) {
        String s = prefs(c).getString(k, null);
        if (s == null) return null;
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final Set<String> tentate = new java.util.HashSet<String>();

    /** Il menu nella lingua dell'app: se e' stato letto in un'altra lingua viene tradotto (una volta) e salvato. */
    static JsonObject dati(Context c) {
        JsonObject base = leggiJson(c, "json");
        if (base == null) return null;
        String lingua = Lingua.INSTANCE.inglese() ? "en" : "it";
        if (base.has("lingua") && base.get("lingua").getAsString().equals(lingua)) return base;
        JsonObject tradotto = leggiJson(c, "json_" + lingua);
        if (tradotto != null) return tradotto;
        traduci(c, base, lingua);
        return base;
    }

    private static void traduci(Context contesto, final JsonObject base, final String lingua) {
        final Context ctx = contesto.getApplicationContext();
        synchronized (tentate) {
            if (inCorso || tentate.contains(lingua)) return;
            tentate.add(lingua);
        }
        final Context lc = Lingua.INSTANCE.contesto(ctx);
        final String chiave = ChiaveAi.leggi(ctx);
        if (chiave == null) return;
        inCorso = true;
        fase = Ui.s(lc, "menu_traduco");
        new Thread(new Runnable() {
            @Override
            public void run() {
                EstrattoreDieta ed = new EstrattoreDieta(chiave, Motore.INSTANCE.modelli(ctx), Motore.INSTANCE.messaggi(lc));
                try {
                    // testi da tradurre: nomi dei pasti, alimenti e quantita'
                    List<String> testi = new ArrayList<String>();
                    for (JsonElement ge : base.getAsJsonArray("giorni")) {
                        for (JsonElement pe : ge.getAsJsonObject().getAsJsonArray("pasti")) {
                            JsonObject po = pe.getAsJsonObject();
                            aggiungi(testi, po, "nome");
                            for (JsonElement ve : po.getAsJsonArray("voci")) {
                                aggiungi(testi, ve.getAsJsonObject(), "n");
                                aggiungi(testi, ve.getAsJsonObject(), "q");
                            }
                        }
                    }
                    JsonArray in = new JsonArray();
                    for (String s : testi) in.add(s);
                    JsonObject pr = new JsonObject();
                    pr.add("t", EstrattoreDieta.array(EstrattoreDieta.tipo("STRING")));
                    String r = ed.chiama("Traduci ogni testo della lista " + (lingua.equals("en") ? "in inglese" : "in italiano")
                                    + ": sono nomi di pasti, alimenti e quantità di una dieta. Usa i nomi usati nei supermercati e nelle "
                                    + "diete italiane (es. Breakfast = Colazione, Morning snack = Spuntino, Afternoon snack = Merenda, "
                                    + "Pre-night = Pre-nanna, Fiber vegetables = Verdure, 1 cup = 1 tazzina). Lascia invariati marche, "
                                    + "numeri e unità (g, ml). Se un testo è già nella lingua giusta lascialo com'è. Rispondi con \"t\": "
                                    + "le traduzioni nello STESSO ordine e nello STESSO numero dei testi ricevuti.",
                            null, in.toString(), EstrattoreDieta.oggetto(pr, "t"));
                    JsonArray out = JsonParser.parseString(r).getAsJsonObject().getAsJsonArray("t");
                    if (out.size() != testi.size()) throw new Exception("traduzione incompleta (" + out.size() + "/" + testi.size() + ")");
                    java.util.Map<String, String> mappa = new java.util.HashMap<String, String>();
                    for (int i = 0; i < testi.size(); i++) mappa.put(testi.get(i), out.get(i).getAsString());
                    JsonObject copia = base.deepCopy();
                    for (JsonElement ge : copia.getAsJsonArray("giorni")) {
                        for (JsonElement pe : ge.getAsJsonObject().getAsJsonArray("pasti")) {
                            JsonObject po = pe.getAsJsonObject();
                            sostituisci(po, "nome", mappa);
                            for (JsonElement ve : po.getAsJsonArray("voci")) {
                                sostituisci(ve.getAsJsonObject(), "n", mappa);
                                sostituisci(ve.getAsJsonObject(), "q", mappa);
                            }
                        }
                    }
                    copia.addProperty("lingua", lingua);
                    prefs(ctx).edit().putString("json_" + lingua, copia.toString()).apply();
                } catch (Throwable e) {
                    errore = Ui.s(lc, "menu_errore", String.valueOf(e.getMessage() != null ? e.getMessage() : e));
                } finally {
                    UsoAi.aggiungi(ctx, ed.richieste, 0);
                    inCorso = false;
                    fase = "";
                    avvisa();
                }
            }
        }).start();
    }

    private static void aggiungi(List<String> l, JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) return;
        String s = o.get(k).getAsString().trim();
        if (!s.isEmpty() && !l.contains(s)) l.add(s);
    }

    private static void sostituisci(JsonObject o, String k, java.util.Map<String, String> m) {
        if (!o.has(k) || o.get(k).isJsonNull()) return;
        String s = m.get(o.get(k).getAsString().trim());
        if (s != null && !s.trim().isEmpty()) o.addProperty(k, s.trim());
    }

    static void ascolta(Runnable r) {
        synchronized (ascolti) {
            if (!ascolti.contains(r)) ascolti.add(r);
        }
    }

    static void smetti(Runnable r) {
        synchronized (ascolti) {
            ascolti.remove(r);
        }
    }

    static void avvisa() {
        Stima.tocca();
        main.post(new Runnable() {
            @Override
            public void run() {
                List<Runnable> l;
                synchronized (ascolti) {
                    l = new ArrayList<Runnable>(ascolti);
                }
                for (Runnable r : l) r.run();
            }
        });
    }

    /** Voci per la lista della spesa salvate quando e' stato caricato il PDF (null se non ce ne sono). */
    static List<DietaLettore.Voce> vociSalvate(Context c) {
        String s = prefs(c).getString("voci", null);
        if (s == null) return null;
        try {
            List<DietaLettore.Voce> l = new ArrayList<DietaLettore.Voce>();
            for (JsonElement e : JsonParser.parseString(s).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                DietaLettore.Voce v = new DietaLettore.Voce();
                v.giorno = o.get("g").getAsString();
                v.persona = o.get("p").getAsString();
                v.nome = o.get("n").getAsString();
                v.quantita = o.get("q").getAsDouble();
                v.unita = o.get("u").getAsString();
                v.reparto = o.get("r").getAsString();
                l.add(v);
            }
            return l.isEmpty() ? null : l;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Dieta arrivata dall'altra persona del gruppo: aggiorna Menu, Impostazioni e Lista. */
    static void dietaArrivata() {
        synchronized (tentate) {
            tentate.clear();
        }
        errore = "";
        avvisa();
    }

    static String fonte(Context c) {
        JsonObject j = leggiJson(c, "json");
        return j != null && j.has("fonte") ? j.get("fonte").getAsString() : null;
    }

    /** Sezione "Dieta" delle Impostazioni: chiamata dalla schermata Compose. */
    public static void sezioneImpostazioni(androidx.compose.runtime.Composer composer) {
        androidx.compose.ui.Modifier m = androidx.compose.foundation.layout.SizeKt.fillMaxWidth(
                androidx.compose.ui.Modifier.Companion, 1f);
        kotlin.jvm.functions.Function1 f = new FabbricaImpostazioni();
        androidx.compose.ui.viewinterop.AndroidView_androidKt.<android.view.View>AndroidView(f, m, null, composer, 0, 4);
    }

    public static final class FabbricaImpostazioni implements kotlin.jvm.functions.Function1<Object, Object> {
        @Override
        public Object invoke(Object c) {
            return new ImpostazioniDieta((Context) c);
        }
    }

    // ------------------------------------------------------------------ lettura del PDF

    static void avvia(Context contesto, final Uri uri) {
        final Context ctx = contesto.getApplicationContext();
        if (inCorso) return;
        final Context lc = Lingua.INSTANCE.contesto(ctx);
        final String chiave = ChiaveAi.leggi(ctx);
        if (chiave == null) {
            errore = Ui.s(lc, "menu_chiave");
            avvisa();
            return;
        }
        inCorso = true;
        errore = "";
        fase = Ui.s(lc, "menu_lettura");
        avvisa();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String nome = nomeFile(ctx, uri);
                    byte[] pdf = DueDiete.leggi(ctx, uri);
                    final String pdf64 = Base64.encodeToString(pdf, Base64.NO_WRAP);
                    // prima il menu, poi le voci per la lista della spesa (in fila: il piano gratuito ha un limite al minuto)
                    JsonObject j = estrai(ctx, lc, pdf64, chiave);
                    fase = Ui.s(lc, "menu_fase_spesa");
                    avvisa();
                    List<DietaLettore.Voce> voci;
                    EstrattoreDieta ed = new EstrattoreDieta(chiave, Motore.INSTANCE.modelli(ctx), Motore.INSTANCE.messaggi(lc));
                    try {
                        voci = ed.estrai(pdf64);
                    } finally {
                        UsoAi.aggiungi(ctx, ed.richieste, 0);
                    }
                    JsonArray va = new JsonArray();
                    for (DietaLettore.Voce v : voci) {
                        JsonObject o = new JsonObject();
                        o.addProperty("g", v.giorno);
                        o.addProperty("p", v.persona);
                        o.addProperty("n", v.nome);
                        o.addProperty("q", v.quantita);
                        o.addProperty("u", v.unita);
                        o.addProperty("r", v.reparto);
                        va.add(o);
                    }
                    j.addProperty("fonte", nome);
                    j.addProperty("lingua", Lingua.INSTANCE.inglese() ? "en" : "it");
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", j.toString())
                            .putString("voci", va.toString()).remove("json_it").remove("json_en").apply();
                    synchronized (tentate) {
                        tentate.clear();
                    }
                    Condivisi.inviaDieta(ctx); // la vede anche l'altra persona del gruppo
                } catch (Gemini.Errore e) {
                    errore = Ui.s(lc, "menu_errore", e.getMessage());
                } catch (Throwable t) {
                    errore = Ui.s(lc, "menu_errore", String.valueOf(t.getMessage() != null ? t.getMessage() : t));
                } finally {
                    inCorso = false;
                    fase = "";
                    avvisa();
                }
            }
        }).start();
    }

    private static String nomeFile(Context ctx, Uri uri) {
        String nome = "dieta";
        try {
            android.database.Cursor c = ctx.getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                if (c.moveToFirst() && c.getString(0) != null) nome = c.getString(0);
                c.close();
            }
        } catch (Throwable ignored) {
        }
        if (nome.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) nome = nome.substring(0, nome.length() - 4);
        return nome;
    }

    private static JsonObject estrai(Context ctx, final Context lc, final String pdf64, String chiave) throws Exception {
        final EstrattoreDieta ed = new EstrattoreDieta(chiave, Motore.INSTANCE.modelli(ctx), Motore.INSTANCE.messaggi(lc));
        final boolean en = Lingua.INSTANCE.inglese();
        try {
            // 1) giorni e persone
            JsonObject p1 = new JsonObject();
            p1.add("giorni", EstrattoreDieta.array(EstrattoreDieta.tipo("STRING")));
            p1.add("persone", EstrattoreDieta.array(EstrattoreDieta.tipo("STRING")));
            String r1 = ed.chiama("Leggi la dieta. Restituisci \"giorni\": i nomi dei giorni nell'ordine, scritti esattamente come nel "
                            + "documento (lista vuota se è un unico schema giornaliero, senza giorni); \"persone\": le sigle o i nomi delle "
                            + "persone se la dieta è per più persone (es. [\"A\",\"B\"]), lista vuota se è per una persona sola. "
                            + "Ignora note e sezioni finali.",
                    pdf64, "Giorni e persone di questa dieta.", EstrattoreDieta.oggetto(p1, "giorni", "persone"));
            JsonObject o1 = JsonParser.parseString(r1).getAsJsonObject();
            List<String> giorni = new ArrayList<String>();
            for (JsonElement e : o1.getAsJsonArray("giorni")) {
                String g = e.getAsString().trim();
                if (!g.isEmpty() && !giorni.contains(g) && giorni.size() < 14) giorni.add(g);
            }
            final Set<String> persone = new LinkedHashSet<String>();
            for (JsonElement e : o1.getAsJsonArray("persone")) {
                String p = e.getAsString().trim();
                if (!p.isEmpty() && persone.size() < 4) persone.add(p);
            }
            if (giorni.isEmpty()) giorni.add("");

            // 2) un giorno per richiesta, 4 in parallelo
            JsonObject voce = new JsonObject();
            voce.add("p", EstrattoreDieta.tipo("STRING"));
            voce.add("n", EstrattoreDieta.tipo("STRING"));
            voce.add("q", EstrattoreDieta.tipo("STRING"));
            JsonObject pasto = new JsonObject();
            pasto.add("nome", EstrattoreDieta.tipo("STRING"));
            pasto.add("ora", EstrattoreDieta.tipo("STRING"));
            pasto.add("voci", EstrattoreDieta.array(EstrattoreDieta.oggetto(voce, "p", "n", "q")));
            JsonObject p2 = new JsonObject();
            p2.add("pasti", EstrattoreDieta.array(EstrattoreDieta.oggetto(pasto, "nome", "ora", "voci")));
            final JsonObject schema = EstrattoreDieta.oggetto(p2, "pasti");
            String chi = persone.isEmpty() ? "La dieta è per una persona sola: \"p\" = \"\"."
                    : "Persone: " + persone + ". \"p\" = SOLO la sigla breve della persona (es. \"A\" o \"B\", non \"Person A\"), "
                    + "una voce per persona. Se la dieta ha una colonna per persona, ogni alimento appartiene alla persona della sua colonna.";
            final String istr = "Trascrivi FEDELMENTE tutti i pasti del giorno richiesto, nell'ordine del documento, senza saltare nulla "
                    + "(colazione, spuntini, pranzo, merenda, pre-allenamento, cena, pre-nanna...). Per ogni pasto: \"nome\" del pasto "
                    + (en ? "in inglese" : "in italiano") + ", \"ora\" se indicata (altrimenti \"\"), \"voci\" = ogni alimento con "
                    + "\"n\" nome dell'alimento " + (en ? "in inglese" : "in italiano") + " (breve, con la marca o il dettaglio utile tra "
                    + "parentesi) e \"q\" quantità come scritta con l'unità (es. \"200 ml\", \"60 g\", \"1 tazzina\"; \"\" se manca). "
                    + "Non convertire e non sommare. " + chi + " Se una riga dice \"X o Y\" e indica cosa mangia ciascuno "
                    + "(es. \"A 125 g yogurt · B 200 ml latte\"), ogni persona ha SOLO il suo alimento. Ignora le quantità 0.";
            ExecutorService es = Executors.newFixedThreadPool(4);
            List<Future<JsonArray>> fut = new ArrayList<Future<JsonArray>>();
            final int tot = giorni.size();
            final int[] fatti = {0};
            for (final String g : giorni) {
                fut.add(es.submit(new Callable<JsonArray>() {
                    @Override
                    public JsonArray call() throws Exception {
                        String richiesta = g.isEmpty() ? "Trascrivi i pasti di questo schema giornaliero."
                                : "Trascrivi SOLO i pasti del giorno \"" + g + "\". Ignora gli altri giorni.";
                        String r = ed.chiama(istr, pdf64, richiesta, schema);
                        synchronized (fatti) {
                            fatti[0]++;
                            fase = Ui.s(lc, "menu_fase", fatti[0], tot);
                        }
                        avvisa();
                        return JsonParser.parseString(r).getAsJsonObject().getAsJsonArray("pasti");
                    }
                }));
            }
            es.shutdown();
            JsonArray outGiorni = new JsonArray();
            try {
                for (int i = 0; i < giorni.size(); i++) {
                    JsonObject gg = new JsonObject();
                    gg.addProperty("nome", giorni.get(i));
                    gg.add("pasti", fut.get(i).get());
                    outGiorni.add(gg);
                }
            } catch (java.util.concurrent.ExecutionException e) {
                for (Future<JsonArray> f : fut) f.cancel(true);
                if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
                throw e;
            }

            // giorno della settimana di ciascun giorno della dieta
            boolean tuttiRiconosciuti = true;
            int[] idx = new int[giorni.size()];
            for (int i = 0; i < giorni.size(); i++) {
                idx[i] = giorni.get(i).isEmpty() ? -1 : GiorniDieta.indice(giorni.get(i));
                if (idx[i] < 0) tuttiRiconosciuti = false;
            }
            if (!tuttiRiconosciuti && giorni.size() == 7) for (int i = 0; i < 7; i++) idx[i] = i;
            for (int i = 0; i < giorni.size(); i++) outGiorni.get(i).getAsJsonObject().addProperty("i", idx[i]);

            JsonObject out = new JsonObject();
            JsonArray pp = new JsonArray();
            for (String p : persone) pp.add(p);
            out.add("persone", pp);
            out.add("giorni", outGiorni);
            return out;
        } finally {
            UsoAi.aggiungi(ctx, ed.richieste, 0);
        }
    }

    // ------------------------------------------------------------------ scelta del PDF

    /** Activity trasparente che apre la scelta del PDF e avvia la lettura. */
    public static final class Scelta extends Activity {
        @Override
        protected void onCreate(Bundle stato) {
            super.onCreate(stato);
            if (stato != null) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            try {
                startActivityForResult(i, 9);
            } catch (RuntimeException e) {
                finish();
            }
        }

        @Override
        protected void onActivityResult(int richiesta, int esito, Intent dati) {
            super.onActivityResult(richiesta, esito, dati);
            if (esito == RESULT_OK && dati != null && dati.getData() != null) avvia(this, dati.getData());
            finish();
        }
    }

    static void scegli(Context c) {
        Intent i = new Intent(c, Scelta.class);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
