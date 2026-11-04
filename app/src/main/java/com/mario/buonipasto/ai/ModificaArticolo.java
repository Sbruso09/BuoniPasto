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

import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.Articolo;
import com.mario.buonipasto.data.ListaSpesa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tenendo premuto un articolo: modifica nome e quantita' (con i tasti - e +) oppure eliminalo. */
public final class ModificaArticolo {
    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|g|ml|l|pz|pcs)?\\b",
            Pattern.CASE_INSENSITIVE);
    private static String ultimo;
    private static long quando;

    private ModificaArticolo() {}

    /** Chiamato dalla schermata Lista al posto del dialogo "Eliminare ...?". */
    public static void mostra(final Context ctx, final Articolo a) {
        long ora = android.os.SystemClock.elapsedRealtime();
        if (a.getId().equals(ultimo) && ora - quando < 800) return;
        ultimo = a.getId();
        quando = ora;

        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(ctx, 20);
        box.setPadding(p, p, p, Ui.dp(ctx, 12));
        box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));

        box.addView(Ui.testo(ctx, a.getNome(), 17, Ui.TESTO, true));
        if (a.getFonte().length() > 0) {
            TextView f = Ui.testo(ctx, a.getFonte(), 12, Ui.TESTO_2, false);
            f.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            box.addView(f);
        }

        // ---- nome
        box.addView(etichetta(ctx, Ui.s(ctx, "mod_nome")));
        final EditText nome = campo(ctx, a.getNome(), "");
        nome.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(nome, new LinearLayout.LayoutParams(-1, -2));

        // ---- quantita' con - e +
        box.addView(etichetta(ctx, Ui.s(ctx, "mod_quantita")));
        LinearLayout riga = new LinearLayout(ctx);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        final EditText qta = campo(ctx, a.getQuantita(), Ui.s(ctx, "mod_esempio"));
        qta.setGravity(Gravity.CENTER);
        TextView meno = tondo(ctx, "−");
        TextView piu = tondo(ctx, "+");
        meno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                qta.setText(passo(qta.getText().toString(), -1));
                qta.setSelection(qta.getText().length());
            }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                qta.setText(passo(qta.getText().toString(), +1));
                qta.setSelection(qta.getText().length());
            }
        });
        int s = Ui.dp(ctx, 44);
        riga.addView(meno, new LinearLayout.LayoutParams(s, s));
        LinearLayout.LayoutParams lq = new LinearLayout.LayoutParams(0, -2, 1f);
        lq.leftMargin = Ui.dp(ctx, 10);
        lq.rightMargin = Ui.dp(ctx, 10);
        riga.addView(qta, lq);
        riga.addView(piu, new LinearLayout.LayoutParams(s, s));
        box.addView(riga);

        // ---- salva / elimina / annulla
        TextView salva = Ui.testo(ctx, Ui.s(ctx, "mod_salva"), 17, Color.WHITE, true);
        salva.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
        salva.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        salva.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = nome.getText().toString().trim();
                if (n.isEmpty()) n = a.getNome();
                else n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
                aggiorna(ctx, a, n, qta.getText().toString().trim());
                d.dismiss();
            }
        });
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(-1, -2);
        ls.topMargin = Ui.dp(ctx, 18);
        box.addView(salva, ls);

        TextView elimina = Ui.testo(ctx, Ui.s(ctx, "elimina"), 17, 0xFFFF453A, true);
        elimina.setBackground(Ui.sfondo(ctx, Ui.RIEMPIMENTO, 14));
        elimina.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
        elimina.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ListaSpesa.INSTANCE.elimina(ctx, a.getId());
                d.dismiss();
            }
        });
        LinearLayout.LayoutParams le = new LinearLayout.LayoutParams(-1, -2);
        le.topMargin = Ui.dp(ctx, 8);
        box.addView(elimina, le);

        TextView annulla = Ui.testo(ctx, Ui.s(ctx, "annulla"), 17, Ui.ACCENTO, false);
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
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
    }

    private static TextView etichetta(Context ctx, String t) {
        TextView v = Ui.testo(ctx, t.toUpperCase(Locale.getDefault()), 12, Ui.TESTO_2, false);
        v.setGravity(Gravity.START);
        v.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 16), 0, Ui.dp(ctx, 6));
        return v;
    }

    private static EditText campo(Context ctx, String testo, String suggerimento) {
        EditText e = new EditText(ctx);
        e.setText(testo);
        e.setHint(suggerimento);
        e.setSingleLine(true);
        e.setTextColor(Ui.TESTO);
        e.setHintTextColor(0x4DEBEBF5);
        e.setTextSize(17);
        e.setBackground(Ui.sfondo(ctx, 0xFF1C1C1E, 10));
        int p = Ui.dp(ctx, 12);
        e.setPadding(p, Ui.dp(ctx, 11), p, Ui.dp(ctx, 11));
        return e;
    }

    private static TextView tondo(Context ctx, String t) {
        TextView v = Ui.testo(ctx, t, 24, Ui.ACCENTO, true);
        v.setBackground(Ui.sfondo(ctx, Ui.RIEMPIMENTO, 22));
        v.setGravity(Gravity.CENTER);
        return v;
    }

    /** Cambia il primo numero della quantita' di un passo adatto all'unita' (5/10/50 g, 0,1 kg, 1 pz). */
    static String passo(String testo, int verso) {
        Matcher m = NUMERO.matcher(testo);
        if (!m.find()) return verso > 0 ? (testo.trim().isEmpty() ? "1" : testo) : testo;
        boolean virgola = m.group(1).indexOf(',') >= 0;
        double v = Double.parseDouble(m.group(1).replace(',', '.'));
        String u = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        double step;
        if (u.equals("kg") || u.equals("l")) step = 0.1;
        else if (u.equals("g") || u.equals("ml")) step = v < 100 || (v == 100 && verso < 0) ? 5 : v < 1000 || (v == 1000 && verso < 0) ? 10 : 50;
        else step = 1;
        double nuovo = Math.max(0, Math.round((v + verso * step) / step) * step);
        String t;
        if (step < 1) {
            t = String.format(Locale.ROOT, "%.1f", nuovo);
            if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
            if (virgola || !Lingua.INSTANCE.inglese()) t = t.replace('.', ',');
        } else {
            t = String.valueOf((long) Math.round(nuovo));
        }
        return testo.substring(0, m.start(1)) + t + testo.substring(m.end(1));
    }

    private static void aggiorna(Context ctx, Articolo a, String nome, String quantita) {
        try {
            List<?> attuali = (List<?>) ListaSpesa.INSTANCE.getArticoli().getValue();
            List<Articolo> nuova = new ArrayList<Articolo>();
            for (Object o : attuali) {
                Articolo x = (Articolo) o;
                if (x.getId().equals(a.getId())) {
                    x = new Articolo(x.getId(), nome, quantita, x.getReparto(), x.getPreso(), x.getFonte());
                }
                nuova.add(x);
            }
            Method salva = ListaSpesa.class.getDeclaredMethod("salva", Context.class, List.class);
            salva.setAccessible(true);
            salva.invoke(ListaSpesa.INSTANCE, ctx, nuova);
        } catch (Exception e) {
            android.widget.Toast.makeText(ctx, String.valueOf(e), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
