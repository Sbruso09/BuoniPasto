package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mario.buonipasto.Lingua;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Dopo aver scelto il PDF della dieta chiede per quali giorni della settimana fare la spesa
 * (lun-dom, anche non consecutivi) e per quante persone. Sostituisce la vecchia scelta 3/7/14 giorni.
 */
public final class GiorniDieta {
    /** Giorni scelti: bit 0 = lunedi' ... bit 6 = domenica. */
    static volatile int maschera = 0x7F;

    private static Uri ultimo;
    private static long quando;

    private GiorniDieta() {}

    private static java.lang.ref.WeakReference<Context> schermata;

    /** La schermata Lista registra il suo contesto (serve per mostrare i dialoghi dal pulsante). */
    public static void registra(Context c) {
        if (c != null) schermata = new java.lang.ref.WeakReference<Context>(c);
    }

    /** Pulsante "Crea la lista dalla dieta": giorni e persone, poi la lista si crea dai dati salvati. */
    public static void daSalvato() {
        final Context ctx = schermata != null ? schermata.get() : null;
        if (ctx == null) return;
        Context lc = Lingua.INSTANCE.contesto(ctx);
        if (MenuDieta.inCorso) {
            android.widget.Toast.makeText(ctx, Ui.s(lc, "dieta_attendi"), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (MenuDieta.vociSalvate(ctx) == null) {
            nessunaDieta(ctx, lc);
            return;
        }
        String nome = MenuDieta.fonte(ctx);
        mostra(ctx, null, nome != null ? nome : "dieta");
    }

    private static void nessunaDieta(final Context ctx, Context lc) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
        box.addView(Ui.testo(ctx, Ui.s(lc, "lista_nessuna_dieta_t"), 17, Ui.TESTO, true));
        TextView msg = Ui.testo(ctx, Ui.s(lc, "lista_nessuna_dieta"), 13, Ui.TESTO_2, false);
        msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
        box.addView(msg);
        TextView carica = Ui.testo(ctx, Ui.s(lc, "imp_dieta_carica"), 17, Color.WHITE, true);
        carica.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        carica.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        carica.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                MenuDieta.scegli(ctx);
            }
        });
        box.addView(carica, new LinearLayout.LayoutParams(-1, -2));
        TextView annulla = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
        annulla.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(annulla);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        }
        d.show();
    }

    /** Chiamato dalla schermata Lista appena si sceglie il PDF (al posto del dialogo dei giorni). */
    public static void daPdf(Context ctx, Uri uri) {
        long ora = SystemClock.elapsedRealtime();
        if (uri.equals(ultimo) && ora - quando < 1500) return; // la schermata puo' ricomporsi due volte
        ultimo = uri;
        quando = ora;
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
        if (nome.toLowerCase(Locale.ROOT).endsWith(".pdf")) nome = nome.substring(0, nome.length() - 4);
        mostra(ctx, uri, nome);
    }

    private static void mostra(final Context ctx, final Uri uri, final String nome) {
        final SharedPreferences sp = ctx.getSharedPreferences("dieta_scelte", Context.MODE_PRIVATE);
        final int[] mask = {sp.getInt("giorni", 0x7F)};
        final int[] persone = {sp.getInt("persone", 1)};

        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));

        box.addView(Ui.testo(ctx, Ui.s(ctx, "giorni_titolo"), 17, Ui.TESTO, true));
        TextView msg = Ui.testo(ctx, Ui.s(ctx, "giorni_testo"), 13, Ui.TESTO_2, false);
        msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 14));
        box.addView(msg);

        // ---- i 7 giorni
        final String[] brevi = Ui.s(ctx, "giorni_brevi").split(",");
        final LinearLayout riga = new LinearLayout(ctx);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] tasti = new TextView[7];
        final TextView tutti = Ui.testo(ctx, "", 15, Ui.ACCENTO, false);
        final TextView crea = Ui.testo(ctx, Ui.s(ctx, "giorni_crea"), 17, Color.WHITE, true);
        final Runnable ridisegna = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 7; i++) {
                    boolean on = (mask[0] & (1 << i)) != 0;
                    tasti[i].setBackground(Ui.sfondo(ctx, on ? Ui.ACCENTO : Ui.RIEMPIMENTO, 19));
                    tasti[i].setTextColor(on ? Color.WHITE : Ui.TESTO_2);
                }
                tutti.setText(Ui.s(ctx, mask[0] == 0x7F ? "giorni_nessuno" : "giorni_tutti"));
                crea.setAlpha(mask[0] == 0 ? 0.4f : 1f);
            }
        };
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView t = Ui.testo(ctx, i < brevi.length ? brevi[i] : "?", 15, Ui.TESTO, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mask[0] ^= bit;
                    ridisegna.run();
                }
            });
            tasti[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(ctx, 38), 1f);
            lp.leftMargin = Ui.dp(ctx, 2);
            lp.rightMargin = Ui.dp(ctx, 2);
            riga.addView(t, lp);
        }
        box.addView(riga);
        tutti.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 4));
        tutti.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mask[0] = mask[0] == 0x7F ? 0 : 0x7F;
                ridisegna.run();
            }
        });
        box.addView(tutti);

        // ---- persone
        TextView tp = Ui.testo(ctx, Ui.s(ctx, "persone_titolo"), 15, Ui.TESTO, true);
        tp.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 8));
        box.addView(tp);
        LinearLayout rp = new LinearLayout(ctx);
        rp.setOrientation(LinearLayout.HORIZONTAL);
        rp.setBackground(Ui.sfondo(ctx, Ui.RIEMPIMENTO, 10));
        int q = Ui.dp(ctx, 2);
        rp.setPadding(q, q, q, q);
        final TextView[] tp12 = new TextView[2];
        final Runnable ridisegnaP = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    boolean on = persone[0] == i + 1;
                    tp12[i].setBackground(on ? Ui.sfondo(ctx, 0xFF636366, 8) : null);
                    tp12[i].setTextColor(on ? Ui.TESTO : Ui.TESTO_2);
                }
            }
        };
        for (int i = 0; i < 2; i++) {
            final int n = i + 1;
            TextView t = Ui.testo(ctx, Ui.s(ctx, n == 1 ? "persone_1" : "persone_2"), 14, Ui.TESTO, true);
            t.setPadding(0, Ui.dp(ctx, 7), 0, Ui.dp(ctx, 7));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    persone[0] = n;
                    ridisegnaP.run();
                }
            });
            tp12[i] = t;
            rp.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        box.addView(rp);
        TextView np = Ui.testo(ctx, Ui.s(ctx, "persone_testo"), 12, Ui.TESTO_2, false);
        np.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 16));
        box.addView(np);

        // ---- crea / annulla
        crea.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        crea.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        crea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mask[0] == 0) return;
                sp.edit().putInt("giorni", mask[0]).putInt("persone", persone[0]).apply();
                d.dismiss();
                avvia(ctx, uri, nome, mask[0], persone[0]);
            }
        });
        box.addView(crea, new LinearLayout.LayoutParams(-1, -2));
        TextView annulla = Ui.testo(ctx, Ui.s(ctx, "annulla"), 17, Ui.ACCENTO, false);
        annulla.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(annulla);

        ridisegna.run();
        ridisegnaP.run();
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
        }
        d.show();
    }

    private static void avvia(Context ctx, Uri uri, String nome, int m, int persone) {
        maschera = m;
        PersoneDieta.persone = persone;
        String fonte = nome + " · " + etichetta(ctx, m);
        if (persone >= 2) fonte = Ui.s(ctx, "persone_fonte", fonte);
        if (uri == null) {
            // dieta salvata nelle Impostazioni: la lista si crea subito, senza rileggere il PDF
            List<DietaLettore.Voce> voci = MenuDieta.vociSalvate(ctx);
            if (voci == null) return;
            Context lc = Lingua.INSTANCE.contesto(ctx);
            List<com.mario.buonipasto.data.Articolo> lista = DietaLettore.lista(voci, Integer.bitCount(m), persone, fonte,
                    Lingua.INSTANCE.inglese());
            com.mario.buonipasto.data.ListaSpesa.INSTANCE.aggiungi(ctx, lista);
            android.widget.Toast.makeText(ctx, Ui.s(lc, "lista_creata", lista.size()), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Dieta.class.getMethod("importaDavvero", Context.class, Uri.class, String.class, int.class)
                    .invoke(Dieta.INSTANCE, ctx, uri, fonte, Integer.bitCount(m));
        } catch (Exception e) {
            android.widget.Toast.makeText(ctx, String.valueOf(e), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** "Lun, Mer, Ven" oppure "settimana intera". */
    static String etichetta(Context ctx, int m) {
        if (m == 0x7F) return Ui.s(ctx, "giorni_settimana");
        String[] nomi = Ui.s(ctx, "giorni_nomi").split(",");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((m & (1 << i)) == 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(i < nomi.length ? nomi[i] : String.valueOf(i + 1));
        }
        return b.toString();
    }

    /** Giorno della settimana (0 = lunedi') dal nome scritto nella dieta, -1 se non si riconosce. */
    static int indice(String giorno) {
        String g = Normalizer.normalize(giorno, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
        String[][] radici = {
                {"lun", "mon"}, {"mar", "tue"}, {"mer", "wed"}, {"gio", "thu"},
                {"ven", "fri"}, {"sab", "sat"}, {"dom", "sun"},
        };
        for (String parola : g.split("[^a-z0-9]+")) {
            if (parola.length() < 3) continue;
            for (int i = 0; i < 7; i++) {
                for (String r : radici[i]) {
                    // "mar" solo come martedi' (non "marzo")
                    if (parola.startsWith(r) && !(r.equals("mar") && parola.startsWith("marz"))
                            && !(r.equals("gio") && parola.startsWith("giorn"))) return i;
                }
            }
        }
        // "Giorno 3", "Day 3"
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("(?:giorno|day|g)\\s*([1-7])\\b").matcher(g);
        if (mt.find()) return Integer.parseInt(mt.group(1)) - 1;
        return -1;
    }
}
