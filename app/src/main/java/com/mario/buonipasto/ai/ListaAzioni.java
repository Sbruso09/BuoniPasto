package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.Articolo;
import com.mario.buonipasto.data.ListaSpesa;

import java.util.List;

/** In cima alla Lista: "Elimina selezionati" e "Svuota la lista" (visibili solo se la lista non e' vuota). */
final class ListaAzioni extends LinearLayout {
    private static final int ROSSO = 0xFFFF453A;
    private final Context lc;
    private final TextView elimina, svuota, aggiungi;
    private final LinearLayout rigaElimina;
    private final Runnable aggiorna = new Runnable() {
        @Override
        public void run() {
            stato();
        }
    };

    ListaAzioni(Context c) {
        super(c);
        lc = Lingua.INSTANCE.contesto(c);
        setOrientation(VERTICAL);
        setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 12));
        aggiungi = Ui.testo(c, "+  " + Ui.s(lc, "agg_titolo"), 16, 0xFFFFFFFF, true);
        aggiungi.setBackground(Ui.sfondo(c, Ui.ACCENTO, 12));
        aggiungi.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        aggiungi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AggiungiSpesa.mostra(getContext(), null);
            }
        });
        addView(aggiungi, new LayoutParams(-1, -2));
        rigaElimina = new LinearLayout(c);
        rigaElimina.setOrientation(HORIZONTAL);
        elimina = pillola(c, Ui.s(lc, "lista_elimina_presi"), Ui.ACCENTO);
        svuota = pillola(c, Ui.s(lc, "lista_svuota"), ROSSO);
        LayoutParams a = new LayoutParams(0, -2, 1f);
        a.rightMargin = Ui.dp(c, 5);
        LayoutParams b = new LayoutParams(0, -2, 1f);
        b.leftMargin = Ui.dp(c, 5);
        rigaElimina.addView(elimina, a);
        rigaElimina.addView(svuota, b);
        LayoutParams lr = new LayoutParams(-1, -2);
        lr.topMargin = Ui.dp(c, 10);
        addView(rigaElimina, lr);
        elimina.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (presi() == 0) return;
                final Context ctx = getContext();
                controllaPagamento(ctx, new Runnable() {
                    @Override
                    public void run() {
                        ListaSpesa.INSTANCE.togliPresi(ctx);
                    }
                });
            }
        });
        svuota.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Context ctx = getContext();
                controllaPagamento(ctx, new Runnable() {
                    @Override
                    public void run() {
                        conferma(ctx);
                    }
                });
            }
        });
        stato();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Stima.ascolta(aggiorna);
        stato();
    }

    @Override
    protected void onDetachedFromWindow() {
        Stima.smetti(aggiorna);
        super.onDetachedFromWindow();
    }

    private static List<?> articoli() {
        return (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue();
    }

    private static int presi() {
        int n = 0;
        for (Object o : articoli()) if (((Articolo) o).getPreso()) n++;
        return n;
    }

    private void stato() {
        boolean vuota = articoli().isEmpty();
        rigaElimina.setVisibility(vuota ? GONE : VISIBLE);
        int p = presi();
        elimina.setText(p > 0 ? Ui.s(lc, "lista_elimina_presi") + " (" + p + ")" : Ui.s(lc, "lista_elimina_presi"));
        elimina.setAlpha(p > 0 ? 1f : 0.4f);
    }

    private static TextView pillola(Context c, String t, int colore) {
        TextView v = Ui.testo(c, t, 15, colore, true);
        v.setBackground(Ui.sfondo(c, 0xFF1C1C1E, 12));
        v.setPadding(Ui.dp(c, 8), Ui.dp(c, 11), Ui.dp(c, 8), Ui.dp(c, 11));
        v.setSingleLine(true);
        return v;
    }

    /** Articoli spuntati senza spesa registrata: chiede se aggiungerla prima di eliminarli. */
    private void controllaPagamento(final Context ctx, final Runnable poi) {
        if (!Stima.presiNonPagati(ctx)) {
            poi.run();
            return;
        }
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
        box.addView(Ui.testo(ctx, Ui.s(lc, "pag_titolo"), 17, Ui.TESTO, true));
        TextView msg = Ui.testo(ctx, Ui.s(lc, "pag_testo", presi()), 13, Ui.TESTO_2, false);
        msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
        box.addView(msg);
        TextView agg = Ui.testo(ctx, Ui.s(lc, "agg_titolo"), 17, Color.WHITE, true);
        agg.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        agg.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        agg.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                AggiungiSpesa.mostra(ctx, poi); // registrata la spesa, procede con l'eliminazione
            }
        });
        box.addView(agg, new LayoutParams(-1, -2));
        TextView comunque = Ui.testo(ctx, Ui.s(lc, "pag_comunque"), 17, ROSSO, true);
        comunque.setBackground(Ui.sfondo(ctx, Ui.RIEMPIMENTO, 14));
        comunque.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        comunque.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                Stima.segnaPagati(ctx);
                poi.run();
            }
        });
        LayoutParams lc2 = new LayoutParams(-1, -2);
        lc2.topMargin = Ui.dp(ctx, 8);
        box.addView(comunque, lc2);
        TextView no = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
        no.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
        no.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(no);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        }
        d.show();
    }

    private void conferma(final Context ctx) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
        box.addView(Ui.testo(ctx, Ui.s(lc, "lista_svuota"), 17, Ui.TESTO, true));
        TextView msg = Ui.testo(ctx, Ui.s(lc, "lista_svuota_testo"), 13, Ui.TESTO_2, false);
        msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
        box.addView(msg);
        TextView si = Ui.testo(ctx, Ui.s(lc, "lista_svuota"), 17, Color.WHITE, true);
        si.setBackground(Ui.sfondo(ctx, ROSSO, 14));
        si.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        si.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ListaSpesa.INSTANCE.svuota(ctx);
                d.dismiss();
            }
        });
        box.addView(si, new LayoutParams(-1, -2));
        TextView no = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
        no.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
        no.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(no);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        }
        d.show();
    }
}
