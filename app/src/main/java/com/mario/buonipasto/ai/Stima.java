package com.mario.buonipasto.ai;

import android.content.Context;

import androidx.compose.runtime.MutableIntState;
import androidx.compose.runtime.SnapshotIntStateKt;

import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.Articolo;
import com.mario.buonipasto.data.DatiApp;
import com.mario.buonipasto.data.ListaSpesa;
import com.mario.buonipasto.data.Member;
import com.mario.buonipasto.data.ModelKt;
import com.mario.buonipasto.data.Session;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stima del costo della lista (articoli non ancora presi) con prezzi medi da supermercato italiano,
 * e quanti buoni pasto servono. Mostrata sotto la scheda del PDF.
 */
public final class Stima {
    private static volatile Context app;
    /** Cambia a ogni salvataggio della lista: la schermata lo legge e si aggiorna. */
    private static MutableIntState versione;

    private static final Pattern QTA = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|g|ml|l|pz|pcs)?\\b",
            Pattern.CASE_INSENSITIVE);

    /** parole chiave (it/en) -> euro al kg (o al litro); in ordine, le piu' specifiche prima. */
    private static final Object[][] PREZZI = {
            {12.0, "burro di arachidi", "peanut butter", "crema di arachidi"},
            {5.0, "yogurt greco", "greek yogurt", "skyr"},
            {7.0, "fiocchi di latte", "cottage"},
            {3.5, "yogurt", "yoghurt"},
            {10.0, "spalmabile", "cream cheese", "philadelphia", "philadelfia"},
            {5.0, "album", "egg white"},
            {5.0, "farina d'avena", "farina di avena", "oat flour"},
            {3.5, "fiocchi d'avena", "fiocchi di avena", "oat flake", "oats", "porridge"},
            {8.0, "gallett", "rice cake", "rice cracker"},
            {4.5, "basmati"},
            {5.0, "fette biscottate", "rusk"},
            {20.0, "frutta secca", "nuts", "noci", "mandorle", "almond", "dried fruit"},
            {18.0, "disidratat", "dehydrated", "essiccat"},
            {1.8, "banan"},
            {3.5, "piselli", "peas"},
            {4.0, "legumi secchi", "dry legume", "dried legume", "lenticchie secche"},
            {3.0, "legum", "lentic", "lentil", "ceci", "chickpea", "fagiol", "bean"},
            {2.5, "frutta", "fruit", "mela", "apple", "arancia", "orange", "pera", "kiwi"},
            {3.0, "verdur", "vegetable", "insalata", "salad", "zucchin", "pomodor", "tomato", "spinaci", "broccol"},
            {10.0, "pollo", "chicken"},
            {12.0, "tacchino", "turkey"},
            {15.0, "hamburger", "burger", "manzo", "beef", "vitello", "veal"},
            {18.0, "prosciutto cotto", "cooked ham", "ham"},
            {30.0, "prosciutto crudo", "bresaola"},
            {9.0, "maiale", "pork"},
            {14.0, "tonno", "tuna"},
            {22.0, "salmone", "salmon"},
            {15.0, "pesce", "fish", "merluzzo", "cod", "orata", "branzino"},
            {12.0, "feta"},
            {9.0, "mozzarella"},
            {22.0, "parmigiano", "grana"},
            {6.0, "ricotta"},
            {9.0, "tofu"},
            {1.4, "latte", "milk"},
            {10.0, "olio", "oil"},
            {14.0, "cioccolat", "chocolate"},
            {8.0, "marmellat", "confettur", "jam"},
            {18.0, "cacao", "cocoa"},
            {15.0, "caff", "coffee"},
            {2.0, "passata", "pelati"},
            {4.0, "pan carr", "pan bauletto", "bauletto"},
            {6.0, "piadin"},
            {6.0, "taralli"},
            {6.0, "cracker"},
            {5.0, "biscott", "frollin", "cookie", "biscuit"},
            {10.0, "croissant", "cornett", "brioche"},
            {7.0, "fitness", "cereal", "muesli", "corn flakes"},
            {6.0, "grano saraceno", "buckwheat"},
            {3.5, "cous"},
            {2.5, "riso", "rice"},
            {2.0, "pasta", "spaghett", "penne"},
            {4.0, "pane", "bread"},
            {10.0, "burro", "butter"},
            {12.0, "miele", "honey"},
    };

    private Stima() {}

    private static final java.util.List<Runnable> ascolti = new java.util.ArrayList<Runnable>();
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

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

    /** Avvisa le viste native dopo che la lista e' stata salvata (post: il salvataggio finisce prima). */
    private static void avvisaViste() {
        main.post(new Runnable() {
            @Override
            public void run() {
                java.util.List<Runnable> l;
                synchronized (ascolti) {
                    l = new java.util.ArrayList<Runnable>(ascolti);
                }
                for (Runnable r : l) r.run();
            }
        });
    }

    /** Pulsanti "Elimina selezionati" e "Svuota la lista" in cima alla Lista (chiamato dalla schermata Compose). */
    public static void azioni(androidx.compose.runtime.Composer composer) {
        androidx.compose.ui.Modifier m = androidx.compose.foundation.layout.SizeKt.fillMaxWidth(
                androidx.compose.ui.Modifier.Companion, 1f);
        kotlin.jvm.functions.Function1 f = new FabbricaAzioni();
        androidx.compose.ui.viewinterop.AndroidView_androidKt.<android.view.View>AndroidView(f, m, null, composer, 0, 4);
    }

    public static final class FabbricaAzioni implements kotlin.jvm.functions.Function1<Object, Object> {
        @Override
        public Object invoke(Object c) {
            return new ListaAzioni((Context) c);
        }
    }

    private static MutableIntState stato() {
        if (versione == null) versione = SnapshotIntStateKt.mutableIntStateOf(0);
        return versione;
    }

    /** Agganciato a ListaSpesa.carica. */
    public static void contesto(Context c) {
        if (c != null) app = c.getApplicationContext();
    }

    /** Agganciato a ListaSpesa.salva: la lista e' cambiata. */
    public static void cambiata(Context c, List<?> l) {
        contesto(c);
        try {
            MutableIntState s = stato();
            s.setIntValue(s.getIntValue() + 1);
        } catch (Throwable ignored) {
        }
        avvisaViste();
    }

    /** Qualcosa e' cambiato (es. lettura della dieta): la schermata Lista si aggiorna. */
    public static void tocca() {
        try {
            MutableIntState s = stato();
            s.setIntValue(s.getIntValue() + 1);
        } catch (Throwable ignored) {
        }
    }

    /** Testo sotto la scheda del PDF (vuoto se non c'e' niente da comprare). */
    public static String testo() {
        try {
            stato().getIntValue(); // la schermata si ricompone quando la lista cambia
            Context c = app;
            if (c == null) return "";
            if (MenuDieta.inCorso) return MenuDieta.fase;
            List<?> l = (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue();
            double euro = 0;
            int voci = 0;
            for (Object o : l) {
                Articolo a = (Articolo) o;
                if (a.getPreso()) continue;
                euro += costo(a.getNome(), a.getQuantita(), a.getReparto());
                voci++;
            }
            if (voci == 0) return "";
            double[] f = fattore(c);
            euro *= f[0];
            long cent = Math.round(euro) * 100L;
            long buono = valoreBuono();
            int buoni = (int) Math.ceil((double) cent / buono);
            Context lc = Lingua.INSTANCE.contesto(c);
            String eur = ModelKt.formatEuro(cent).replace(",00", "");
            if (f[1] > 0) {
                return Ui.s(lc, buoni == 1 ? "stima_tarata_1" : "stima_tarata", eur, buoni, ModelKt.formatEuro(buono), (int) f[1]);
            }
            return Ui.s(lc, buoni == 1 ? "stima_testo_1" : "stima_testo", eur, buoni, ModelKt.formatEuro(buono));
        } catch (Throwable t) {
            return "";
        }
    }

    // ------------------------------------------------------------------ taratura sulle spese reali

    private static final int MAX_TARATURE = 10;

    private static android.content.SharedPreferences pt(Context c) {
        return c.getSharedPreferences("stima", Context.MODE_PRIVATE);
    }

    /** {fattore, numero di spese usate}: quanto spendi davvero rispetto ai prezzi medi (1 = come i prezzi medi). */
    static double[] fattore(Context c) {
        try {
            com.google.gson.JsonArray a = com.google.gson.JsonParser.parseString(pt(c).getString("tarature", "[]")).getAsJsonArray();
            double s = 0, r = 0;
            for (com.google.gson.JsonElement e : a) {
                s += e.getAsJsonArray().get(0).getAsDouble();
                r += e.getAsJsonArray().get(1).getAsDouble();
            }
            if (a.size() == 0 || s <= 0) return new double[]{1, 0};
            return new double[]{Math.max(0.6, Math.min(2.5, r / s)), a.size()};
        } catch (RuntimeException e) {
            return new double[]{1, 0};
        }
    }

    /**
     * Controllata ogni pochi secondi: quando aggiungi una spesa nella Home, confronta quanto hai speso davvero
     * con la stima (a prezzi medi) degli articoli spuntati nella lista, cioe' quelli appena comprati.
     */
    public static void calibra(Context c) {
        try {
            app = c.getApplicationContext();
            Session s = DatiApp.INSTANCE.getSession();
            List<com.mario.buonipasto.data.Movement> mov = DatiApp.INSTANCE.getMovements();
            if (s == null || mov == null || mov.isEmpty()) return;
            android.content.SharedPreferences p = pt(c);
            java.util.Set<String> visti = new java.util.HashSet<String>(p.getStringSet("visti", new java.util.HashSet<String>()));
            boolean primaVolta = !p.getBoolean("pronta", false);
            boolean cambiato = false;
            long adesso = System.currentTimeMillis();
            for (com.mario.buonipasto.data.Movement m : mov) {
                if (!visti.add(m.getId())) continue;
                cambiato = true;
                if (primaVolta) continue; // le spese gia' presenti non contano
                if (m.getType() != com.mario.buonipasto.data.MovementType.EXPENSE) continue;
                if (!s.getMemberId().equals(m.getCreatedBy())) continue;
                if (adesso - m.getCreatedAt() > 30 * 60_000L) continue;
                // articoli spuntati = appena comprati
                List<?> l = (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue();
                double base = 0;
                StringBuilder firma = new StringBuilder();
                for (Object o : l) {
                    Articolo a = (Articolo) o;
                    if (!a.getPreso()) continue;
                    base += costo(a.getNome(), a.getQuantita(), a.getReparto());
                    firma.append(a.getId()).append(',');
                }
                if (firma.length() > 0) p.edit().putString("pagata_firma", firma.toString()).apply(); // pagamento registrato
                double reale = m.getTotalCents() / 100.0;
                if (base < 3 || reale <= 0) continue;
                if (firma.toString().equals(p.getString("ultima_firma", ""))) continue; // stessi articoli gia' usati
                com.google.gson.JsonArray a = com.google.gson.JsonParser.parseString(p.getString("tarature", "[]")).getAsJsonArray();
                com.google.gson.JsonArray coppia = new com.google.gson.JsonArray();
                coppia.add(Math.round(base * 100.0) / 100.0);
                coppia.add(reale);
                a.add(coppia);
                while (a.size() > MAX_TARATURE) a.remove(0);
                p.edit().putString("tarature", a.toString()).putString("ultima_firma", firma.toString()).apply();
                Context lc = Lingua.INSTANCE.contesto(c);
                android.widget.Toast.makeText(c, Ui.s(lc, "stima_imparata", ModelKt.formatEuro(m.getTotalCents()),
                        ModelKt.formatEuro(Math.round(base * 100))), android.widget.Toast.LENGTH_LONG).show();
                tocca();
            }
            if (cambiato || primaVolta) {
                if (visti.size() > 2000) visti.clear();
                p.edit().putStringSet("visti", visti).putBoolean("pronta", true).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Id degli articoli spuntati (vuoto se nessuno). */
    static String firmaPresi() {
        StringBuilder f = new StringBuilder();
        for (Object o : (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue()) {
            Articolo a = (Articolo) o;
            if (a.getPreso()) f.append(a.getId()).append(',');
        }
        return f.toString();
    }

    /** true se ci sono articoli spuntati per cui non hai ancora registrato la spesa. */
    static boolean presiNonPagati(Context c) {
        String f = firmaPresi();
        return f.length() > 0 && !f.equals(pt(c).getString("pagata_firma", ""));
    }

    /** La spesa degli articoli spuntati e' stata appena registrata (dalla Lista). */
    static void segnaPagati(Context c) {
        String f = firmaPresi();
        if (f.length() > 0) pt(c).edit().putString("pagata_firma", f).apply();
    }

    /** Stima (tarata) degli articoli spuntati, o di tutta la lista se non ne hai spuntato nessuno. */
    static double stimaPerSpesa(Context c) {
        double presi = 0, tutti = 0;
        for (Object o : (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue()) {
            Articolo a = (Articolo) o;
            double x = costo(a.getNome(), a.getQuantita(), a.getReparto());
            tutti += x;
            if (a.getPreso()) presi += x;
        }
        return (presi > 0 ? presi : tutti) * fattore(c)[0];
    }

    static long valoreBuonoCent() {
        return valoreBuono();
    }

    private static long valoreBuono() {
        try {
            Session s = DatiApp.INSTANCE.getSession();
            List<Member> m = DatiApp.INSTANCE.getMembers();
            if (s != null && m != null) {
                for (Member x : m) if (x.getId().equals(s.getMemberId()) && x.getVoucherCents() > 0) return x.getVoucherCents();
                for (Member x : m) if (x.getVoucherCents() > 0) return x.getVoucherCents();
            }
        } catch (Throwable ignored) {
        }
        return 800; // 8,00 euro
    }

    static double prezzoKg(String nome, String reparto) {
        String n = nome.toLowerCase(Locale.ROOT);
        for (Object[] r : PREZZI) {
            for (int i = 1; i < r.length; i++) if (n.contains((String) r[i])) return (Double) r[0];
        }
        if ("frutta_verdura".equals(reparto)) return 3;
        if ("pane_cereali".equals(reparto)) return 4;
        if ("carne_pesce".equals(reparto)) return 14;
        if ("latticini_uova".equals(reparto)) return 6;
        if ("dispensa".equals(reparto)) return 8;
        if ("surgelati".equals(reparto)) return 5;
        if ("bevande".equals(reparto)) return 2;
        return 5;
    }

    static double prezzoPezzo(String nome) {
        String n = nome.toLowerCase(Locale.ROOT);
        if ((n.contains("uov") || n.contains("egg")) && !n.contains("album") && !n.contains("white")) return 0.30;
        if (n.contains("croissant") || n.contains("cornett") || n.contains("brioche")) return 0.60;
        if (n.contains("banan")) return 0.30;
        if (n.contains("piadin")) return 0.50;
        return 1.5;
    }

    /** Costo stimato di una voce: "690 g", "1,4 kg", "280 ml + 2 pz"; senza quantita' = una confezione. */
    static double costo(String nome, String quantita, String reparto) {
        double kg = prezzoKg(nome, reparto);
        Matcher m = QTA.matcher(quantita == null ? "" : quantita);
        double tot = 0;
        boolean trovato = false;
        while (m.find()) {
            trovato = true;
            double v = Double.parseDouble(m.group(1).replace(',', '.'));
            String u = m.group(2) == null ? "pz" : m.group(2).toLowerCase(Locale.ROOT);
            if (u.equals("g") || u.equals("ml")) tot += v / 1000.0 * kg;
            else if (u.equals("kg") || u.equals("l")) tot += v * kg;
            else tot += v * prezzoPezzo(nome);
        }
        if (!trovato) tot = 2.5; // una confezione
        return tot;
    }
}
