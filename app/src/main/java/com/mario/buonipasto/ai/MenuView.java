package com.mario.buonipasto.ai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mario.buonipasto.Lingua;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Schermata della scheda Menu: i pasti del giorno, che cambiano da soli ogni giorno. */
final class MenuView extends FrameLayout {
    private static final int SFONDO = 0xFF000000, CARTA = 0xFF1C1C1E, SEPARATORE = 0xFF38383A,
            BLU = 0xFF64D2FF, ARANCIO = 0xFFFF9F0A, ROSSO = 0xFFFF453A;

    private final Context lc;
    private final LinearLayout colonna;
    private int giorno = -1;      // 0 = lunedi'
    private int oggiVisto = -1;
    private String persona = "";  // "" = tutte
    private final Runnable aggiorna = new Runnable() {
        @Override
        public void run() {
            disegna();
        }
    };
    private final Runnable mezzanotte = new Runnable() {
        @Override
        public void run() {
            // nuovo giorno: se stavi guardando "oggi" passa al giorno nuovo
            if (giorno == oggiVisto) giorno = oggi();
            disegna();
            programma();
        }
    };

    MenuView(Context c) {
        super(c);
        lc = Lingua.INSTANCE.contesto(c);
        setBackgroundColor(SFONDO);
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        colonna = new LinearLayout(c);
        colonna.setOrientation(LinearLayout.VERTICAL);
        colonna.setPadding(0, altezzaStatus(), 0, Ui.dp(c, 32));
        sv.addView(colonna, new LayoutParams(-1, -2));
        addView(sv, new LayoutParams(-1, -1));
        giorno = oggi();
        disegna();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MenuDieta.ascolta(aggiorna);
        if (giorno == oggiVisto && oggi() != oggiVisto) giorno = oggi();
        disegna();
        programma();
    }

    @Override
    protected void onDetachedFromWindow() {
        MenuDieta.smetti(aggiorna);
        removeCallbacks(mezzanotte);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE && giorno == oggiVisto && oggi() != oggiVisto) {
            giorno = oggi();
            disegna();
        }
    }

    private void programma() {
        removeCallbacks(mezzanotte);
        Calendar c = Calendar.getInstance();
        Calendar dom = (Calendar) c.clone();
        dom.add(Calendar.DAY_OF_YEAR, 1);
        dom.set(Calendar.HOUR_OF_DAY, 0);
        dom.set(Calendar.MINUTE, 0);
        dom.set(Calendar.SECOND, 5);
        postDelayed(mezzanotte, Math.max(1000, dom.getTimeInMillis() - c.getTimeInMillis()));
    }

    private static int oggi() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // 1 = domenica
        return (d + 5) % 7;
    }

    private int altezzaStatus() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return (id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(getContext(), 24)) + Ui.dp(getContext(), 6);
    }

    private Locale locale() {
        return Lingua.INSTANCE.inglese() ? Locale.ENGLISH : Locale.ITALIAN;
    }

    // ------------------------------------------------------------------ disegno

    private void disegna() {
        Context c = getContext();
        colonna.removeAllViews();
        oggiVisto = oggi();
        JsonObject d = MenuDieta.dati(c);

        // ---- titolo + pulsante PDF
        LinearLayout testa = new LinearLayout(c);
        testa.setOrientation(LinearLayout.HORIZONTAL);
        testa.setGravity(Gravity.CENTER_VERTICAL);
        testa.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), 0);
        TextView titolo = sinistra(Ui.testo(c, Ui.s(lc, "tab_menu"), 34, Ui.TESTO, true));
        testa.addView(titolo, new LinearLayout.LayoutParams(0, -2, 1f));
        if (d != null && !MenuDieta.inCorso) {
            FrameLayout tasto = new FrameLayout(c);
            tasto.setBackground(Ui.sfondo(c, 0x3D767680, 19));
            ImageView ic = new ImageView(c);
            int id = getResources().getIdentifier("ic_pdf", "drawable", c.getPackageName());
            if (id != 0) ic.setImageResource(id);
            ic.setImageTintList(ColorStateList.valueOf(Ui.ACCENTO));
            int s = Ui.dp(c, 20);
            tasto.addView(ic, new LayoutParams(s, s, Gravity.CENTER));
            tasto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    MenuDieta.scegli(getContext());
                }
            });
            testa.addView(tasto, new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
        }
        colonna.addView(testa);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, giorno - oggiVisto);
        String data = new SimpleDateFormat("EEEE d MMMM", locale()).format(cal.getTime());
        data = Character.toUpperCase(data.charAt(0)) + data.substring(1);
        if (giorno == oggiVisto) data = Ui.s(lc, "menu_oggi") + " · " + data;
        String sotto = data + (d != null && d.has("fonte") ? "\n" + d.get("fonte").getAsString() : "");
        TextView st = sinistra(Ui.testo(c, sotto, 13, Ui.TESTO_2, false));
        st.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 12));
        colonna.addView(st);

        // ---- stato lettura / errore
        if (MenuDieta.inCorso) {
            LinearLayout r = carta(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            ProgressBar pb = new ProgressBar(c);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENTO));
            r.addView(pb, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            TextView t = sinistra(Ui.testo(c, MenuDieta.fase, 15, Ui.TESTO_2, false));
            t.setPadding(Ui.dp(c, 12), 0, 0, 0);
            r.addView(t);
            colonna.addView(r, margini(c, 0));
        }
        if (!MenuDieta.errore.isEmpty() && !MenuDieta.inCorso) {
            TextView e = sinistra(Ui.testo(c, MenuDieta.errore, 14, ROSSO, false));
            e.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), Ui.dp(c, 10));
            colonna.addView(e);
        }

        if (d == null) {
            if (!MenuDieta.inCorso) vuoto(c);
            return;
        }

        // ---- giorni della settimana
        colonna.addView(strisciaGiorni(c, d));

        // ---- persone
        // persone ricavate dagli alimenti letti (i nomi della prima lettura possono essere diversi, es. "Person A" e "A")
        List<String> persone = personeDa(d);
        if (persone.size() >= 2) colonna.addView(sceltaPersona(c, persone), margini(c, 10));
        if (!persone.contains(persona)) persona = "";

        // ---- pasti
        JsonObject g = trovaGiorno(d, giorno);
        if (g == null || g.getAsJsonArray("pasti").size() == 0) {
            TextView t = Ui.testo(c, Ui.s(lc, "menu_nessun_pasto"), 15, Ui.TESTO_2, false);
            t.setPadding(0, Ui.dp(c, 40), 0, 0);
            colonna.addView(t);
            return;
        }
        for (JsonElement pe : g.getAsJsonArray("pasti")) pasto(c, pe.getAsJsonObject(), persone);
    }

    private static List<String> personeDa(JsonObject d) {
        List<String> l = new ArrayList<String>();
        for (JsonElement ge : d.getAsJsonArray("giorni")) {
            for (JsonElement pe : ge.getAsJsonObject().getAsJsonArray("pasti")) {
                for (JsonElement ve : pe.getAsJsonObject().getAsJsonArray("voci")) {
                    String p = chi(ve.getAsJsonObject());
                    if (!p.isEmpty() && !l.contains(p)) l.add(p);
                }
            }
        }
        java.util.Collections.sort(l);
        return l.size() >= 2 && l.size() <= 4 ? l : new ArrayList<String>();
    }

    /** Sigla della persona di una voce: "Person A", "Persona A", "A" diventano tutte "A". */
    static String chi(JsonObject v) {
        String p = v.has("p") && !v.get("p").isJsonNull() ? v.get("p").getAsString().trim() : "";
        String l = p.toLowerCase(Locale.ROOT);
        for (String pre : new String[]{"person ", "persona ", "persone ", "pers. "}) {
            if (l.startsWith(pre)) p = p.substring(pre.length()).trim();
        }
        int sp = p.indexOf(' ');
        if (sp > 0 && sp <= 2) p = p.substring(0, sp); // "A (Mario)" -> "A"
        if (p.startsWith("(")) p = "";
        return p;
    }

    private static JsonObject trovaGiorno(JsonObject d, int giorno) {
        JsonArray gg = d.getAsJsonArray("giorni");
        for (JsonElement e : gg) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("i") && o.get("i").getAsInt() == giorno) return o;
        }
        // schema giornaliero unico: vale per tutti i giorni
        if (gg.size() == 1 && gg.get(0).getAsJsonObject().get("i").getAsInt() < 0) return gg.get(0).getAsJsonObject();
        return null;
    }

    private void vuoto(Context c) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(c, 32), Ui.dp(c, 70), Ui.dp(c, 32), 0);
        b.addView(Ui.testo(c, Ui.s(lc, "menu_vuoto_t"), 20, Ui.TESTO, true));
        TextView t = Ui.testo(c, Ui.s(lc, "menu_vuoto"), 15, Ui.TESTO_2, false);
        t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 22));
        b.addView(t);
        TextView bt = Ui.testo(c, Ui.s(lc, "menu_carica"), 17, Color.WHITE, true);
        bt.setBackground(Ui.sfondo(c, Ui.ACCENTO, 14));
        bt.setPadding(Ui.dp(c, 28), Ui.dp(c, 13), Ui.dp(c, 28), Ui.dp(c, 13));
        bt.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MenuDieta.scegli(getContext());
            }
        });
        b.addView(bt, new LinearLayout.LayoutParams(-2, -2));
        colonna.addView(b);
    }

    private View strisciaGiorni(Context c, JsonObject d) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 6));
        String[] brevi = Ui.s(lc, "giorni_brevi").split(",");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -oggiVisto); // lunedi' di questa settimana
        for (int i = 0; i < 7; i++) {
            final int gi = i;
            boolean scelto = i == giorno, ora = i == oggiVisto;
            LinearLayout cella = new LinearLayout(c);
            cella.setOrientation(LinearLayout.VERTICAL);
            cella.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView lettera = Ui.testo(c, i < brevi.length ? brevi[i] : "", 12, ora ? Ui.ACCENTO : Ui.TESTO_2, true);
            cella.addView(lettera);
            TextView num = Ui.testo(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 17,
                    scelto ? Color.WHITE : ora ? Ui.ACCENTO : (trovaGiorno(d, i) == null ? 0x4DEBEBF5 : Ui.TESTO), scelto || ora);
            GradientDrawable cerchio = new GradientDrawable();
            cerchio.setShape(GradientDrawable.OVAL);
            cerchio.setColor(scelto ? Ui.ACCENTO : Color.TRANSPARENT);
            num.setBackground(cerchio);
            int s = Ui.dp(c, 36);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.topMargin = Ui.dp(c, 4);
            cella.addView(num, lp);
            cella.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    giorno = gi;
                    disegna();
                }
            });
            r.addView(cella, new LinearLayout.LayoutParams(0, -2, 1f));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return r;
    }

    private View sceltaPersona(Context c, List<String> persone) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBackground(Ui.sfondo(c, Ui.RIEMPIMENTO, 9));
        int q = Ui.dp(c, 2);
        r.setPadding(q, q, q, q);
        List<String> scelte = new ArrayList<String>();
        scelte.add("");
        scelte.addAll(persone);
        for (final String p : scelte) {
            boolean on = p.equals(persona);
            TextView t = Ui.testo(c, p.isEmpty() ? Ui.s(lc, "menu_entrambi") : p, 13, on ? Ui.TESTO : Ui.TESTO_2, true);
            t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            if (on) t.setBackground(Ui.sfondo(c, 0xFF636366, 7));
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    persona = p;
                    disegna();
                }
            });
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return r;
    }

    private void pasto(Context c, JsonObject p, List<String> persone) {
        String nome = p.get("nome").getAsString();
        String ora = p.has("ora") ? p.get("ora").getAsString().trim() : "";
        TextView h = sinistra(Ui.testo(c, (nome + (ora.isEmpty() ? "" : " · " + ora)).toUpperCase(locale()), 13, Ui.TESTO_2, false));
        h.setPadding(Ui.dp(c, 32), Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 6));
        colonna.addView(h);

        LinearLayout card = carta(c);
        JsonArray voci = p.getAsJsonArray("voci");
        boolean due = persone.size() >= 2 && persona.isEmpty();
        if (due) {
            // tabella: una colonna per persona
            LinearLayout riga = new LinearLayout(c);
            riga.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < persone.size(); i++) {
                if (i > 0) {
                    View sep = new View(c);
                    sep.setBackgroundColor(SEPARATORE);
                    riga.addView(sep, new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 0.5f)), -1));
                }
                LinearLayout col = new LinearLayout(c);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
                String chi = persone.get(i);
                TextView tag = Ui.testo(c, chi, 12, i % 2 == 0 ? BLU : ARANCIO, true);
                tag.setGravity(Gravity.START);
                col.addView(tag);
                int n = 0;
                for (JsonElement ve : voci) {
                    JsonObject v = ve.getAsJsonObject();
                    String cv = chi(v);
                    if (!cv.isEmpty() && !chi.equalsIgnoreCase(cv)) continue; // senza persona = per tutti
                    col.addView(cella(c, v));
                    n++;
                }
                if (n == 0) {
                    TextView t = sinistra(Ui.testo(c, "—", 15, 0x4DEBEBF5, false));
                    t.setPadding(0, Ui.dp(c, 6), 0, 0);
                    col.addView(t);
                }
                riga.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            card.addView(riga);
        } else {
            int n = 0;
            for (JsonElement ve : voci) {
                JsonObject v = ve.getAsJsonObject();
                if (!persona.isEmpty() && !chi(v).isEmpty() && !persona.equalsIgnoreCase(chi(v))) continue;
                if (n > 0) {
                    View sep = new View(c);
                    sep.setBackgroundColor(SEPARATORE);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 0.5f)));
                    lp.leftMargin = Ui.dp(c, 16);
                    card.addView(sep, lp);
                }
                LinearLayout r = new LinearLayout(c);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(Ui.dp(c, 16), Ui.dp(c, 11), Ui.dp(c, 16), Ui.dp(c, 11));
                r.addView(sinistra(Ui.testo(c, v.get("n").getAsString(), 16, Ui.TESTO, false)),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                TextView q = Ui.testo(c, v.get("q").getAsString(), 15, Ui.TESTO_2, false);
                q.setPadding(Ui.dp(c, 10), 0, 0, 0);
                r.addView(q);
                card.addView(r);
                n++;
            }
            if (n == 0) return; // pasto solo dell'altra persona
        }
        colonna.addView(card, margini(c, 0));
    }

    private View cella(Context c, JsonObject v) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, Ui.dp(c, 6), 0, 0);
        l.addView(sinistra(Ui.testo(c, v.get("n").getAsString(), 14, Ui.TESTO, false)));
        String q = v.get("q").getAsString();
        if (!q.isEmpty()) l.addView(sinistra(Ui.testo(c, q, 13, Ui.TESTO_2, true)));
        return l;
    }

    private static LinearLayout carta(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Ui.sfondo(c, CARTA, 12));
        l.setClipToOutline(true);
        return l;
    }

    private static LinearLayout.LayoutParams margini(Context c, int sopra) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = Ui.dp(c, 16);
        lp.rightMargin = Ui.dp(c, 16);
        lp.topMargin = Ui.dp(c, sopra);
        return lp;
    }

    private static TextView sinistra(TextView t) {
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }
}
