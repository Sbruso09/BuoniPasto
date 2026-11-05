package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.DatiApp;
import com.mario.buonipasto.data.ModelKt;
import com.mario.buonipasto.data.Movement;
import com.mario.buonipasto.data.MovementType;
import com.mario.buonipasto.data.Session;
import com.mario.buonipasto.data.Split;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Toccando una spesa nella Home: inviala a BuyForTime (con buoni e parte in contanti) o condividila come testo. */
public final class Ponte {
    private static final String BUYFORTIME = "com.example.appsalario";
    private static WeakReference<Context> attivita;

    private Ponte() {}

    /** Chiamato da MainActivity.onCreate: serve un contesto di Activity per mostrare la scelta. */
    public static void registra(Context c) {
        if (c != null) attivita = new WeakReference<Context>(c);
        if (c != null) Condivisi.avvia(c); // dieta e chiavi AI condivise con l'altra persona del gruppo
    }

    /** onClick della riga della spesa (creato dalla schermata Compose). */
    public static final class Tocco implements kotlin.jvm.functions.Function0<Object> {
        private final Movement m;

        public Tocco(Movement m) {
            this.m = m;
        }

        @Override
        public Object invoke() {
            try {
                tocca(m);
            } catch (Throwable ignored) {
            }
            return kotlin.Unit.INSTANCE;
        }
    }

    static void tocca(final Movement m) {
        final Context ctx = attivita != null ? attivita.get() : null;
        if (ctx == null || m == null || m.getType() != MovementType.EXPENSE) return;
        Context lc = Lingua.INSTANCE.contesto(ctx);

        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
        String titolo = m.getNote().trim().isEmpty() ? Ui.s(lc, "ponte_spesa") : m.getNote().trim();
        box.addView(Ui.testo(ctx, titolo, 17, Ui.TESTO, true));
        TextView sub = Ui.testo(ctx, ModelKt.formatEuro(m.getTotalCents()) + " · " + data(m.getDateMillis()), 13, Ui.TESTO_2, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 16));
        box.addView(sub);

        TextView invia = Ui.testo(ctx, Ui.s(lc, "ponte_invia"), 17, Color.WHITE, true);
        invia.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        invia.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        invia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                invia(ctx, m);
            }
        });
        box.addView(invia, new LinearLayout.LayoutParams(-1, -2));

        TextView condividi = Ui.pillola(ctx, Ui.s(lc, "ponte_condividi"));
        condividi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                condividi(ctx, m);
            }
        });
        LinearLayout.LayoutParams lc2 = new LinearLayout.LayoutParams(-1, -2);
        lc2.topMargin = Ui.dp(ctx, 8);
        box.addView(condividi, lc2);

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

    private static String data(long ms) {
        return new SimpleDateFormat("d MMMM yyyy", Lingua.INSTANCE.inglese() ? java.util.Locale.ENGLISH : java.util.Locale.ITALIAN)
                .format(new Date(ms));
    }

    private static void invia(Context ctx, Movement m) {
        Intent i = new Intent("com.example.appsalario.IMPORTA_SPESA");
        i.setClassName(BUYFORTIME, BUYFORTIME + ".ImportaSpesa");
        i.putExtra("bp_id", m.getId());
        i.putExtra("bp_nota", m.getNote());
        i.putExtra("bp_data", m.getDateMillis());
        i.putExtra("bp_buoni", m.getVouchers());
        i.putExtra("bp_valore_cent", m.getVoucherCents());
        i.putExtra("bp_extra_cent", m.getExtraCents());
        i.putExtra("bp_meta", m.getSplit() == Split.HALF);
        Session s = DatiApp.INSTANCE.getSession();
        i.putExtra("bp_pagato_da_me", s != null && s.getMemberId().equals(m.getPayerId()));
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Context lc = Lingua.INSTANCE.contesto(ctx);
            Toast.makeText(ctx, Ui.s(lc, "ponte_manca"), Toast.LENGTH_LONG).show();
            condividi(ctx, m);
        }
    }

    private static void condividi(Context ctx, Movement m) {
        Context lc = Lingua.INSTANCE.contesto(ctx);
        String nome = m.getNote().trim().isEmpty() ? Ui.s(lc, "ponte_spesa") : m.getNote().trim();
        StringBuilder t = new StringBuilder();
        t.append(nome).append('\n')
                .append(data(m.getDateMillis())).append('\n')
                .append(Ui.s(lc, "ponte_testo_buoni", m.getVouchers(), ModelKt.formatEuro(m.getVoucherCents())));
        if (m.getExtraCents() > 0) t.append('\n').append(Ui.s(lc, "ponte_testo_extra", ModelKt.formatEuro(m.getExtraCents())));
        t.append('\n').append(Ui.s(lc, "ponte_testo_totale", ModelKt.formatEuro(m.getTotalCents())));
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.toString());
        try {
            ctx.startActivity(Intent.createChooser(i, null));
        } catch (RuntimeException ignored) {
        }
    }
}
