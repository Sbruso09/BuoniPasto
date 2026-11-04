package com.mario.buonipasto.ai;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mario.buonipasto.Lingua;

/** Sezione "Dieta" delle Impostazioni: il PDF si carica qui una volta, menu e lista usano i dati salvati. */
final class ImpostazioniDieta extends LinearLayout {
    private final Context lc;
    private final Runnable aggiorna = new Runnable() {
        @Override
        public void run() {
            disegna();
        }
    };

    ImpostazioniDieta(Context c) {
        super(c);
        lc = Lingua.INSTANCE.contesto(c);
        setOrientation(VERTICAL);
        disegna();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MenuDieta.ascolta(aggiorna);
        disegna();
    }

    @Override
    protected void onDetachedFromWindow() {
        MenuDieta.smetti(aggiorna);
        super.onDetachedFromWindow();
    }

    private void disegna() {
        Context c = getContext();
        removeAllViews();

        TextView h = sx(Ui.testo(c, Ui.s(lc, "imp_dieta").toUpperCase(), 13, Ui.TESTO_2, false));
        h.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 7));
        addView(h);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(Ui.sfondo(c, 0xFF1C1C1E, 16));
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.leftMargin = Ui.dp(c, 16);
        lp.rightMargin = Ui.dp(c, 16);

        // riga: icona, titolo, nome del PDF
        LinearLayout riga = new LinearLayout(c);
        riga.setOrientation(HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        riga.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.sfondo(c, 0xFFFF453A, 7));
        ImageView ic = new ImageView(c);
        int id = getResources().getIdentifier("ic_pdf", "drawable", c.getPackageName());
        if (id != 0) ic.setImageResource(id);
        ic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        tile.addView(ic, new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
        riga.addView(tile, new LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        LinearLayout testi = new LinearLayout(c);
        testi.setOrientation(VERTICAL);
        testi.setPadding(Ui.dp(c, 12), 0, 0, 0);
        testi.addView(sx(Ui.testo(c, Ui.s(lc, "imp_dieta_pdf"), 17, Ui.TESTO, false)));
        String fonte = MenuDieta.fonte(c);
        boolean salvata = fonte != null && MenuDieta.vociSalvate(c) != null;
        String stato = MenuDieta.inCorso ? MenuDieta.fase
                : fonte != null ? fonte + (salvata ? "" : " · " + Ui.s(lc, "imp_dieta_rileggi")) : Ui.s(lc, "imp_dieta_nessuno");
        testi.addView(sx(Ui.testo(c, stato, 13, Ui.TESTO_2, false)));
        riga.addView(testi, new LayoutParams(0, -2, 1f));
        if (MenuDieta.inCorso) {
            ProgressBar pb = new ProgressBar(c);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENTO));
            riga.addView(pb, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        }
        card.addView(riga);

        if (!MenuDieta.inCorso) {
            View sep = new View(c);
            sep.setBackgroundColor(0xFF38383A);
            LayoutParams sl = new LayoutParams(-1, Math.max(1, Ui.dp(c, 0.5f)));
            sl.leftMargin = Ui.dp(c, 58);
            card.addView(sep, sl);
            TextView tasto = sx(Ui.testo(c, Ui.s(lc, fonte != null ? "imp_dieta_cambia" : "imp_dieta_carica"), 17, Ui.ACCENTO, false));
            tasto.setPadding(Ui.dp(c, 58), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
            tasto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    MenuDieta.scegli(getContext());
                }
            });
            card.addView(tasto);
        }
        addView(card, lp);

        String nota = !MenuDieta.inCorso && !MenuDieta.errore.isEmpty() ? MenuDieta.errore : Ui.s(lc, "imp_dieta_nota");
        TextView f = sx(Ui.testo(c, nota, 13, !MenuDieta.inCorso && !MenuDieta.errore.isEmpty() ? 0xFFFF453A : Ui.TESTO_2, false));
        f.setPadding(Ui.dp(c, 20), Ui.dp(c, 7), Ui.dp(c, 20), 0);
        addView(f);
    }

    private static TextView sx(TextView t) {
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }
}
