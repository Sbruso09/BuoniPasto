package com.mario.buonipasto.ai;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Piccoli aiuti per disegnare dialoghi in stile iOS (stessa palette dell'app). */
final class Ui {
    static final int ACCENTO = 0xFF5E5CE6, SUPERFICIE = 0xFF2C2C2E, RIEMPIMENTO = 0x3D767680,
            TESTO = 0xFFFFFFFF, TESTO_2 = 0x99EBEBF5;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String s(Context c, String nome, Object... arg) {
        int id = c.getResources().getIdentifier(nome, "string", c.getPackageName());
        if (id == 0) return nome;
        return arg.length == 0 ? c.getString(id) : c.getString(id, arg);
    }

    static GradientDrawable sfondo(Context c, int colore, float raggio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colore);
        g.setCornerRadius(dp(c, raggio));
        return g;
    }

    static TextView testo(Context c, String t, float sp, int colore, boolean grassetto) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colore);
        v.setGravity(Gravity.CENTER);
        int id = c.getResources().getIdentifier(grassetto ? "inter_semibold" : "inter_regular", "font", c.getPackageName());
        Typeface f = null;
        try {
            if (id != 0) f = c.getResources().getFont(id);
        } catch (RuntimeException ignored) {
        }
        if (f != null) v.setTypeface(f);
        else if (grassetto) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Pulsante a pillola grigia con testo viola, come nell'app. */
    static TextView pillola(Context c, String t) {
        TextView v = testo(c, t, 17, ACCENTO, true);
        v.setBackground(sfondo(c, RIEMPIMENTO, 14));
        v.setPadding(0, dp(c, 13), 0, dp(c, 13));
        return v;
    }
}
