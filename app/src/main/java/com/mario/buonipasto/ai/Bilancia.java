package com.mario.buonipasto.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Bilancia in stile iOS: giogo con riflesso metallico, perno, due piatti appesi che restano in verticale,
 * con l'iniziale di ciascuna persona. Si inclina verso chi deve ricevere, con una molla smorzata
 * (un piccolo rimbalzo, come le animazioni di iOS).
 */
final class Bilancia extends View {
    private static final float MAX_GRADI = 13f;
    private static final float RIGIDITA = 150f, SMORZAMENTO = 0.42f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final String[] nomi = {"", ""};
    private final int[] colori = {0xFF5E5CE6, 0xFFFF375F};

    private float angolo, velocita, obiettivo;
    private long ultimoFrame;
    private boolean animando;
    private Typeface grassetto, normale;

    Bilancia(Context c) {
        super(c);
        try {
            int b = c.getResources().getIdentifier("inter_semibold", "font", c.getPackageName());
            int n = c.getResources().getIdentifier("inter_regular", "font", c.getPackageName());
            if (b != 0) grassetto = c.getResources().getFont(b);
            if (n != 0) normale = c.getResources().getFont(n);
        } catch (RuntimeException ignored) {
        }
        if (grassetto == null) grassetto = Typeface.DEFAULT_BOLD;
        if (normale == null) normale = Typeface.DEFAULT;
    }

    void persone(String sinistra, String destra, int coloreSx, int coloreDx) {
        nomi[0] = sinistra;
        nomi[1] = destra;
        colori[0] = coloreSx;
        colori[1] = coloreDx;
        invalidate();
    }

    /** peso in [-1, 1]: positivo = il piatto di sinistra scende (a sinistra c'e' chi deve ricevere). */
    void peso(float peso) {
        float nuovo = -Math.max(-1f, Math.min(1f, peso)) * MAX_GRADI;
        if (Math.abs(nuovo - obiettivo) < 0.01f && !animando) return;
        obiettivo = nuovo;
        avvia();
    }

    /** Piccola spinta, ad esempio quando cambi tra buoni e soldi. */
    void scuoti() {
        velocita += (angolo >= obiettivo ? -1 : 1) * 38f;
        avvia();
    }

    private void avvia() {
        if (animando) return;
        animando = true;
        ultimoFrame = 0;
        postOnAnimation(passo);
    }

    private final Runnable passo = new Runnable() {
        @Override
        public void run() {
            long ora = System.nanoTime();
            float dt = ultimoFrame == 0 ? 1f / 60f : Math.min(0.05f, (ora - ultimoFrame) / 1e9f);
            ultimoFrame = ora;
            // molla smorzata integrata a piccoli passi
            float c = 2f * SMORZAMENTO * (float) Math.sqrt(RIGIDITA);
            int n = 4;
            for (int i = 0; i < n; i++) {
                float h = dt / n;
                float acc = -RIGIDITA * (angolo - obiettivo) - c * velocita;
                velocita += acc * h;
                angolo += velocita * h;
            }
            invalidate();
            if (Math.abs(velocita) < 0.02f && Math.abs(angolo - obiettivo) < 0.02f) {
                angolo = obiettivo;
                velocita = 0;
                animando = false;
                invalidate();
            } else {
                postOnAnimation(this);
            }
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(passo);
        animando = false;
        angolo = obiettivo;
        velocita = 0;
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(178));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float px = w / 2f, py = dp(40);
        float meta = Math.min(w * 0.36f, dp(150));
        float base = h - dp(8);

        // ---- piedistallo
        path.reset();
        path.moveTo(px - dp(3), py);
        path.lineTo(px + dp(3), py);
        path.lineTo(px + dp(9), base - dp(6));
        path.lineTo(px - dp(9), base - dp(6));
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(px - dp(9), 0, px + dp(9), 0,
                new int[]{0xFF3A3A3C, 0xFF636366, 0xFF3A3A3C}, null, Shader.TileMode.CLAMP));
        cv.drawPath(path, p);
        r.set(px - dp(38), base - dp(8), px + dp(38), base);
        p.setShader(new LinearGradient(0, base - dp(8), 0, base, 0xFF636366, 0xFF2C2C2E, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, dp(4), dp(4), p);
        p.setShader(null);

        // ---- estremi del giogo dopo la rotazione
        double rad = Math.toRadians(angolo);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float[] ex = {px - cos * meta, px + cos * meta};
        float[] ey = {py - sin * meta, py + sin * meta};

        // ---- piatti (appesi: restano verticali)
        for (int i = 0; i < 2; i++) piatto(cv, ex[i], ey[i], i);

        // ---- giogo
        cv.save();
        cv.rotate(angolo, px, py);
        r.set(px - meta - dp(4), py - dp(3.5f), px + meta + dp(4), py + dp(3.5f));
        p.setShader(new LinearGradient(0, py - dp(3.5f), 0, py + dp(3.5f),
                new int[]{0xFFE5E5EA, 0xFFAEAEB2, 0xFF8E8E93}, null, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, dp(3.5f), dp(3.5f), p);
        p.setShader(null);
        p.setColor(0xFFD1D1D6);
        cv.drawCircle(px - meta, py, dp(4.5f), p);
        cv.drawCircle(px + meta, py, dp(4.5f), p);
        cv.restore();

        // ---- perno
        p.setColor(0xFFF2F2F7);
        cv.drawCircle(px, py, dp(9), p);
        p.setColor(Ui.ACCENTO);
        cv.drawCircle(px, py, dp(4), p);
    }

    private void piatto(Canvas cv, float x, float y, int i) {
        float appeso = dp(40), largo = dp(76), fondo = dp(18);
        float yp = y + appeso;
        int col = colori[i];
        // fili
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1.4f));
        p.setColor(0xFF8E8E93);
        cv.drawLine(x, y, x - largo / 2f + dp(4), yp, p);
        cv.drawLine(x, y, x + largo / 2f - dp(4), yp, p);
        // persona seduta sul piatto
        p.setStyle(Paint.Style.FILL);
        float ra = dp(15);
        p.setColor(col);
        cv.drawCircle(x, yp - ra + dp(3), ra, p);
        p.setColor(Color.WHITE);
        p.setTypeface(grassetto);
        p.setTextSize(dp(15));
        p.setTextAlign(Paint.Align.CENTER);
        String ini = nomi[i].isEmpty() ? "?" : nomi[i].substring(0, 1).toUpperCase();
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(ini, x, yp - ra + dp(3) - (fm.ascent + fm.descent) / 2f, p);
        // piatto a coppa
        path.reset();
        path.moveTo(x - largo / 2f, yp);
        path.quadTo(x, yp + fondo * 1.6f, x + largo / 2f, yp);
        path.close();
        p.setShader(new LinearGradient(0, yp, 0, yp + fondo, 0xFF48484A, 0xFF2C2C2E, Shader.TileMode.CLAMP));
        cv.drawPath(path, p);
        p.setShader(null);
        r.set(x - largo / 2f, yp - dp(2), x + largo / 2f, yp + dp(2));
        p.setColor(col);
        cv.drawRoundRect(r, dp(2), dp(2), p);
        // nome
        p.setColor(col);
        p.setTypeface(grassetto);
        p.setTextSize(dp(13));
        cv.drawText(nomi[i], x, yp + fondo + dp(16), p);
    }
}
