package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.DatiApp;
import com.mario.buonipasto.data.Member;
import com.mario.buonipasto.data.ModelKt;
import com.mario.buonipasto.data.Session;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Aggiungi spesa" dalla Lista: stessa spesa della Home (buoni, extra, divisione, nota), con i buoni suggeriti dalla stima. */
final class AggiungiSpesa {
    private AggiungiSpesa() {}

    static void mostra(final Context ctx, final Runnable dopo) {
        final Context lc = Lingua.INSTANCE.contesto(ctx);
        final Session s = DatiApp.INSTANCE.getSession();
        Member me = null, altro = null;
        List<Member> mm = DatiApp.INSTANCE.getMembers();
        if (s != null && mm != null) {
            for (Member m : mm) {
                if (m.getId().equals(s.getMemberId())) me = m;
                else altro = m;
            }
        }
        if (s == null || me == null) {
            Toast.makeText(ctx, Ui.s(lc, "agg_nessun_gruppo"), Toast.LENGTH_LONG).show();
            return;
        }
        final Member io = me;
        final long buonoCent = io.getVoucherCents() > 0 ? io.getVoucherCents() : Stima.valoreBuonoCent();
        double stima = Stima.stimaPerSpesa(ctx);
        final int[] buoni = {Math.max(1, (int) Math.ceil(stima * 100 / buonoCent))};
        final String[] divisione = {"HALF"};

        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
        box.addView(Ui.testo(ctx, Ui.s(lc, "agg_titolo"), 17, Ui.TESTO, true));
        if (stima > 0) {
            TextView st = Ui.testo(ctx, Ui.s(lc, "agg_suggerimento", ModelKt.formatEuro(Math.round(stima * 100)), buoni[0]), 13, Ui.TESTO_2, false);
            st.setPadding(0, Ui.dp(ctx, 6), 0, 0);
            box.addView(st);
        }

        // ---- buoni
        box.addView(etichetta(ctx, Ui.s(lc, "agg_buoni", ModelKt.formatEuro(buonoCent))));
        LinearLayout riga = new LinearLayout(ctx);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        final TextView n = Ui.testo(ctx, String.valueOf(buoni[0]), 22, Ui.TESTO, true);
        n.setBackground(Ui.sfondo(ctx, 0xFF1C1C1E, 10));
        n.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
        TextView meno = tondo(ctx, "−"), piu = tondo(ctx, "+");
        meno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buoni[0] = Math.max(0, buoni[0] - 1);
                n.setText(String.valueOf(buoni[0]));
            }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buoni[0]++;
                n.setText(String.valueOf(buoni[0]));
            }
        });
        int sz = Ui.dp(ctx, 44);
        riga.addView(meno, new LinearLayout.LayoutParams(sz, sz));
        LinearLayout.LayoutParams lq = new LinearLayout.LayoutParams(0, -2, 1f);
        lq.leftMargin = Ui.dp(ctx, 10);
        lq.rightMargin = Ui.dp(ctx, 10);
        riga.addView(n, lq);
        riga.addView(piu, new LinearLayout.LayoutParams(sz, sz));
        box.addView(riga);

        // ---- extra e nota
        box.addView(etichetta(ctx, Ui.s(lc, "agg_extra")));
        final EditText extra = campo(ctx, "0,00");
        extra.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(extra, new LinearLayout.LayoutParams(-1, -2));
        box.addView(etichetta(ctx, Ui.s(lc, "agg_nota")));
        final EditText nota = campo(ctx, Ui.s(lc, "agg_nota_es"));
        nota.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(nota, new LinearLayout.LayoutParams(-1, -2));

        // ---- divisione
        box.addView(etichetta(ctx, Ui.s(lc, "agg_divisione")));
        LinearLayout seg = new LinearLayout(ctx);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(Ui.sfondo(ctx, Ui.RIEMPIMENTO, 10));
        int q = Ui.dp(ctx, 2);
        seg.setPadding(q, q, q, q);
        final String[] chiavi = {"HALF", "PAYER_ONLY", "OTHER_ONLY"};
        String nomeAltro = altro != null ? altro.getName() : "?";
        String[] testi = {Ui.s(lc, "split_meta"), Ui.s(lc, "split_mia"), Ui.s(lc, "split_di", nomeAltro)};
        final TextView[] tasti = new TextView[3];
        final Runnable ridisegna = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    boolean on = chiavi[i].equals(divisione[0]);
                    tasti[i].setBackground(on ? Ui.sfondo(ctx, 0xFF636366, 8) : null);
                    tasti[i].setTextColor(on ? Ui.TESTO : Ui.TESTO_2);
                }
            }
        };
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = Ui.testo(ctx, testi[i], 13, Ui.TESTO, true);
            t.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 7), Ui.dp(ctx, 4), Ui.dp(ctx, 7));
            t.setSingleLine(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    divisione[0] = chiavi[k];
                    ridisegna.run();
                }
            });
            tasti[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (altro == null) seg.setVisibility(View.GONE);
        box.addView(seg);
        ridisegna.run();

        // ---- salva
        TextView salva = Ui.testo(ctx, Ui.s(lc, "agg_salva"), 17, Color.WHITE, true);
        salva.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        salva.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        salva.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Long ex = ModelKt.parseCents(String.valueOf(extra.getText()).trim().isEmpty() ? "0" : String.valueOf(extra.getText()));
                if (ex == null) {
                    Toast.makeText(ctx, Ui.s(lc, "agg_extra_errato"), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (buoni[0] == 0 && ex == 0) {
                    Toast.makeText(ctx, Ui.s(lc, "agg_vuota"), Toast.LENGTH_SHORT).show();
                    return;
                }
                Calendar oggi = Calendar.getInstance();
                oggi.set(Calendar.HOUR_OF_DAY, 0);
                oggi.set(Calendar.MINUTE, 0);
                oggi.set(Calendar.SECOND, 0);
                oggi.set(Calendar.MILLISECOND, 0);
                long adesso = System.currentTimeMillis();
                Map<String, Object> m = new HashMap<String, Object>();
                m.put("type", "EXPENSE");
                m.put("payerId", io.getId());
                m.put("payerName", io.getName());
                m.put("receiverName", "");
                m.put("vouchers", buoni[0]);
                m.put("voucherCents", buonoCent);
                m.put("extraCents", ex);
                m.put("split", divisione[0]);
                m.put("note", String.valueOf(nota.getText()).trim());
                m.put("dateMillis", oggi.getTimeInMillis());
                m.put("createdAt", adesso);
                m.put("createdBy", io.getId());
                FirebaseFirestore.getInstance().collection("groups").document(s.getGroupCode())
                        .collection("movements").document().set(m);
                Stima.segnaPagati(ctx);
                d.dismiss();
                Toast.makeText(ctx, Ui.s(lc, "agg_fatto"), Toast.LENGTH_SHORT).show();
                if (dopo != null) dopo.run();
            }
        });
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(-1, -2);
        ls.topMargin = Ui.dp(ctx, 20);
        box.addView(salva, ls);
        TextView annulla = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
        annulla.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(annulla);
        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        sv.addView(box);
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
    }

    private static TextView etichetta(Context c, String t) {
        TextView v = Ui.testo(c, t.toUpperCase(), 12, Ui.TESTO_2, false);
        v.setGravity(Gravity.START);
        v.setPadding(Ui.dp(c, 4), Ui.dp(c, 16), 0, Ui.dp(c, 6));
        return v;
    }

    private static EditText campo(Context c, String suggerimento) {
        EditText e = new EditText(c);
        e.setHint(suggerimento);
        e.setSingleLine(true);
        e.setTextColor(Ui.TESTO);
        e.setHintTextColor(0x4DEBEBF5);
        e.setTextSize(17);
        e.setBackground(Ui.sfondo(c, 0xFF1C1C1E, 10));
        int p = Ui.dp(c, 12);
        e.setPadding(p, Ui.dp(c, 11), p, Ui.dp(c, 11));
        return e;
    }

    private static TextView tondo(Context c, String t) {
        TextView v = Ui.testo(c, t, 24, Ui.ACCENTO, true);
        v.setBackground(Ui.sfondo(c, Ui.RIEMPIMENTO, 22));
        v.setGravity(Gravity.CENTER);
        return v;
    }
}
