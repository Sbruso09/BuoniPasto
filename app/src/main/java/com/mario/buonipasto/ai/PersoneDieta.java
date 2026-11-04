package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Prima di creare la lista dalla dieta chiede per quante persone e' la spesa.
 * La dieta del PDF e' per una persona: con 2 persone le quantita' vengono raddoppiate.
 */
public final class PersoneDieta {
    static volatile int persone = 1;

    private PersoneDieta() {}

    /** Chiamato al posto del vecchio Dieta.importa: mostra la scelta e poi avvia la lettura. */
    public static void chiedi(final Context ctx, final Uri uri, final String nome, final int giorni) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));

        box.addView(Ui.testo(ctx, Ui.s(ctx, "persone_titolo"), 17, Ui.TESTO, true));
        TextView msg = Ui.testo(ctx, Ui.s(ctx, "persone_testo"), 13, Ui.TESTO_2, false);
        msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
        box.addView(msg);

        for (int n = 1; n <= 2; n++) {
            final int scelta = n;
            TextView b = Ui.pillola(ctx, Ui.s(ctx, n == 1 ? "persone_1" : "persone_2"));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    avvia(ctx, uri, nome, giorni, scelta);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(ctx, 8);
            box.addView(b, lp);
        }
        TextView annulla = Ui.testo(ctx, Ui.s(ctx, "annulla"), 17, Ui.ACCENTO, false);
        annulla.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6));
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

    private static void avvia(Context ctx, Uri uri, String nome, int giorni, int n) {
        persone = n;
        String fonte = n >= 2 ? Ui.s(ctx, "persone_fonte", nome) : nome;
        try {
            Dieta.class.getMethod("importaDavvero", Context.class, Uri.class, String.class, int.class)
                    .invoke(Dieta.INSTANCE, ctx, uri, fonte, giorni);
        } catch (Exception e) {
            android.widget.Toast.makeText(ctx, String.valueOf(e), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Aggiunto in fondo alle istruzioni per la lista della spesa. */
    public static String aggiungi(String istruzioni) {
        if (persone < 2) return istruzioni;
        return istruzioni + "\n\nIMPORTANTE: la dieta è per UNA persona, ma la spesa è per " + persone
                + " persone che seguono la stessa dieta. Moltiplica TUTTE le quantità per " + persone
                + " (es. \"petto di pollo 700 g\" diventa \"petto di pollo 1,4 kg\", \"uova 6\" diventa \"uova 12\")"
                + " e arrotonda alle confezioni che si trovano al supermercato.";
    }
}
