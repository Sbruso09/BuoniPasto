package com.mario.buonipasto.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.data.Member;
import com.mario.buonipasto.data.ModelKt;
import com.mario.buonipasto.data.Movement;
import com.mario.buonipasto.data.MovementType;
import com.mario.buonipasto.data.Split;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Due saldi separati tra le due persone del gruppo:
 *  - BUONI: contati in numero di buoni. Le spese pagate con i buoni si pareggiano da sole
 *    (la prossima spesa con i buoni la fa chi e' in debito);
 *  - SOLDI: in euro. Spese pagate senza buoni, parte "in piu'" pagata in contanti/carta e rimborsi;
 *    si ripagano solo con soldi veri ("Segna come saldato").
 * Le spese registrate prima di questo aggiornamento restano nel vecchio conto unico, che confluisce nei soldi
 * (prima si saldava tutto in soldi). Il momento del passaggio e' uguale per tutti e due i telefoni.
 */
public final class Saldi {
    private static final int VERDE = 0xFF30D158, ARANCIO = 0xFFFF9F0A;
    /** Nota dei rimborsi che ripagano in soldi un debito di buoni: "#buoni:<mezzi buoni>". */
    static final String SEGNO_BUONI = "#buoni:";

    private Saldi() {}

    /** Risultato: buoni in mezzi buoni e soldi in centesimi, dal punto di vista di {@code io} (positivo = l'altro mi deve). */
    static long[] calcola(List<Movement> movimenti, String io, long passaggio) {
        long mezziBuoni = 0, mezziCent = 0;
        for (Movement m : movimenti) {
            boolean mio = m.getPayerId().equals(io);
            if (m.getCreatedAt() < passaggio) {
                // vecchio conto unico (buoni + soldi in euro): finisce nei soldi
                long credito;
                if (m.getType() == MovementType.SETTLEMENT) credito = m.getTotalCents() * 2;
                else if (m.getSplit() == Split.HALF) credito = m.getTotalCents();
                else if (m.getSplit() == Split.OTHER_ONLY) credito = m.getTotalCents() * 2;
                else credito = 0;
                mezziCent += mio ? credito : -credito;
                continue;
            }
            if (m.getType() == MovementType.SETTLEMENT && m.getNote().startsWith(SEGNO_BUONI)) {
                // debito di buoni ripagato in soldi: azzera i buoni, non tocca il saldo dei soldi
                long n = 0;
                try {
                    n = Long.parseLong(m.getNote().substring(SEGNO_BUONI.length()).trim());
                } catch (NumberFormatException ignored) {
                }
                mezziBuoni += mio ? n : -n;
                continue;
            }
            if (m.getType() == MovementType.SETTLEMENT) {
                long c = m.getTotalCents() * 2; // i rimborsi sono sempre in soldi
                mezziCent += mio ? c : -c;
                continue;
            }
            long fattore = m.getSplit() == Split.HALF ? 1 : m.getSplit() == Split.OTHER_ONLY ? 2 : 0;
            long b = m.getVouchers() * fattore;      // in mezzi buoni
            long s = m.getExtraCents() * fattore;    // in mezzi centesimi
            mezziBuoni += mio ? b : -b;
            mezziCent += mio ? s : -s;
        }
        return new long[]{mezziBuoni, mezziCent / 2};
    }

    static String buoniTesto(Context lc, long mezzi) {
        long a = Math.abs(mezzi);
        String n = a % 2 == 0 ? String.valueOf(a / 2) : String.format(Locale.ITALY, "%.1f", a / 2.0);
        return Ui.s(lc, a == 2 ? "saldi_buono_1" : "saldi_buoni_n", n);
    }

    // ------------------------------------------------------------------ dalla schermata Compose

    /** Sostituisce il vecchio pannello del saldo nella Home (stessa firma). */
    public static void pannello(List<Member> membri, Member io, Member altro, List<Movement> movimenti, String gruppo,
                                kotlin.jvm.functions.Function0 suSaldato, androidx.compose.runtime.Composer composer, int cambiato) {
        androidx.compose.ui.Modifier m = androidx.compose.foundation.layout.SizeKt.fillMaxWidth(
                androidx.compose.ui.Modifier.Companion, 1f);
        kotlin.jvm.functions.Function1 f = new Fabbrica();
        kotlin.jvm.functions.Function1 u = new Aggiorna(membri, io, altro, movimenti, gruppo);
        androidx.compose.ui.viewinterop.AndroidView_androidKt.<View>AndroidView(f, m, u, composer, 0, 0);
    }

    public static final class Fabbrica implements kotlin.jvm.functions.Function1<Object, Object> {
        @Override
        public Object invoke(Object c) {
            return new Pannello((Context) c);
        }
    }

    public static final class Aggiorna implements kotlin.jvm.functions.Function1<Object, Object> {
        final List<Member> membri;
        final Member io, altro;
        final List<Movement> movimenti;
        final String gruppo;

        public Aggiorna(List<Member> membri, Member io, Member altro, List<Movement> movimenti, String gruppo) {
            this.membri = membri;
            this.io = io;
            this.altro = altro;
            this.movimenti = movimenti;
            this.gruppo = gruppo;
        }

        @Override
        public Object invoke(Object v) {
            try {
                ((Pannello) v).mostra(this);
            } catch (Throwable ignored) {
            }
            return kotlin.Unit.INSTANCE;
        }
    }

    // ------------------------------------------------------------------ pannello

    /** Modo mostrato dal pannello: true = buoni, false = soldi (resta uguale quando la Home si aggiorna). */
    private static boolean modoBuoni = true;

    static final class Pannello extends LinearLayout {
        private final Context lc;
        private Aggiorna ultimo;
        private final LinearLayout card, mancante;
        private final FrameLayout segmento;
        private final View cursore;
        private final TextView segB, segS, chi, valore, spiega, saldato, tileB, tileS;
        private final Bilancia bilancia;
        private long buoni, soldi;
        private double mostrato = Double.NaN;
        private boolean mostratoBuoni = true;
        private android.animation.ValueAnimator numero;

        Pannello(Context c) {
            super(c);
            lc = Lingua.INSTANCE.contesto(c);
            setOrientation(VERTICAL);
            setClipChildren(false);

            // ---- gruppo senza la seconda persona
            mancante = carta(c);
            addView(mancante, margini(c, 0));

            // ---- pannello dei saldi
            card = carta(c);
            addView(card, margini(c, 0));

            // segmento iOS con cursore che scorre
            segmento = new FrameLayout(c);
            segmento.setBackground(Ui.sfondo(c, Ui.RIEMPIMENTO, 9));
            int q = Ui.dp(c, 2);
            segmento.setPadding(q, q, q, q);
            cursore = new View(c);
            cursore.setBackground(Ui.sfondo(c, 0xFF636366, 7));
            cursore.setElevation(Ui.dp(c, 2));
            segmento.addView(cursore, new FrameLayout.LayoutParams(0, -1));
            LinearLayout etichette = new LinearLayout(c);
            etichette.setOrientation(HORIZONTAL);
            segB = Ui.testo(c, Ui.s(lc, "saldi_buoni_titolo"), 14, Ui.TESTO, true);
            segS = Ui.testo(c, Ui.s(lc, "saldi_soldi_titolo"), 14, Ui.TESTO, true);
            for (TextView x : new TextView[]{segB, segS}) {
                x.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
                x.setElevation(Ui.dp(c, 3));
                etichette.addView(x, new LayoutParams(0, -2, 1f));
            }
            segB.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scegli(true);
                }
            });
            segS.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scegli(false);
                }
            });
            segmento.addView(etichette, new FrameLayout.LayoutParams(-1, -2));
            card.addView(segmento, new LayoutParams(-1, -2));
            segmento.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    int w = (r - l - segmento.getPaddingLeft() - segmento.getPaddingRight()) / 2;
                    if (cursore.getLayoutParams().width != w) {
                        cursore.getLayoutParams().width = w;
                        cursore.requestLayout();
                    }
                    cursore.setTranslationX(modoBuoni ? 0 : w);
                }
            });

            chi = sx(Ui.testo(c, "", 15, Ui.TESTO_2, false));
            chi.setPadding(0, Ui.dp(c, 18), 0, 0);
            card.addView(chi);
            valore = sx(Ui.testo(c, "", 40, Ui.TESTO, true));
            valore.setLetterSpacing(-0.02f);
            card.addView(valore);
            spiega = sx(Ui.testo(c, "", 13, Ui.TESTO_2, false));
            spiega.setPadding(0, Ui.dp(c, 4), 0, 0);
            card.addView(spiega);

            bilancia = new Bilancia(c);
            LayoutParams lb = new LayoutParams(-1, -2);
            lb.topMargin = Ui.dp(c, 6);
            card.addView(bilancia, lb);

            // riepilogo dei due saldi, toccabili
            LinearLayout tiles = new LinearLayout(c);
            tiles.setOrientation(HORIZONTAL);
            tileB = tile(c);
            tileS = tile(c);
            LayoutParams a = new LayoutParams(0, -2, 1f);
            a.rightMargin = Ui.dp(c, 5);
            LayoutParams b = new LayoutParams(0, -2, 1f);
            b.leftMargin = Ui.dp(c, 5);
            tiles.addView(tileB, a);
            tiles.addView(tileS, b);
            tileB.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scegli(true);
                }
            });
            tileS.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scegli(false);
                }
            });
            LayoutParams lt = new LayoutParams(-1, -2);
            lt.topMargin = Ui.dp(c, 8);
            card.addView(tiles, lt);

            saldato = Ui.testo(c, Ui.s(lc, "segna_saldato"), 17, Color.WHITE, true);
            saldato.setBackground(Ui.sfondo(c, Ui.ACCENTO, 12));
            saldato.setPadding(0, Ui.dp(c, 13), 0, Ui.dp(c, 13));
            premibile(saldato);
            saldato.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ultimo == null) return;
                    if (modoBuoni && buoni != 0) confermaBuoni(getContext(), ultimo, buoni);
                    else if (!modoBuoni && soldi != 0) conferma(getContext(), ultimo, soldi);
                }
            });
            LayoutParams ls = new LayoutParams(-1, -2);
            ls.topMargin = Ui.dp(c, 14);
            card.addView(saldato, ls);

            Condivisi.avvia(c);
        }

        private TextView tile(Context c) {
            TextView t = sx(Ui.testo(c, "", 13, Ui.TESTO, false));
            t.setBackground(Ui.sfondo(c, 0xFF2C2C2E, 10));
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
            t.setLineSpacing(Ui.dp(c, 1), 1f);
            premibile(t);
            return t;
        }

        /** Rimpicciolisce un attimo al tocco, come i pulsanti di iOS. */
        private static void premibile(final View v) {
            v.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View x, android.view.MotionEvent e) {
                    if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        x.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.85f).setDuration(90).start();
                    } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                            || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                        x.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
                    }
                    return false;
                }
            });
        }

        private void scegli(boolean buoniScelti) {
            if (modoBuoni == buoniScelti) return;
            modoBuoni = buoniScelti;
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int w = cursore.getWidth();
            cursore.animate().translationX(modoBuoni ? 0 : w).setDuration(320)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f)).start();
            // il testo sfuma e rientra, la bilancia oscilla verso il nuovo saldo
            final View[] blocco = {chi, valore, spiega};
            for (View x : blocco) x.animate().alpha(0f).translationY(-Ui.dp(getContext(), 4)).setDuration(110).start();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    mostratoBuoni = !modoBuoni; // forza il conteggio del numero da zero
                    mostrato = 0;
                    aggiornaContenuti(true);
                    for (View x : blocco) {
                        x.setTranslationY(Ui.dp(getContext(), 6));
                        x.animate().alpha(1f).translationY(0).setDuration(260)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                    }
                }
            }, 120);
            bilancia.scuoti();
        }

        void mostra(final Aggiorna d) {
            ultimo = d;
            Context c = getContext();
            if (d.altro == null) {
                card.setVisibility(GONE);
                mancante.setVisibility(VISIBLE);
                mancante.removeAllViews();
                mancante.addView(sx(Ui.testo(c, Ui.s(lc, "manca_seconda"), 20, Ui.TESTO, true)));
                TextView t = sx(Ui.testo(c, Ui.s(lc, "manca_seconda_testo", d.gruppo), 15, Ui.TESTO_2, false));
                t.setPadding(0, Ui.dp(c, 6), 0, 0);
                mancante.addView(t);
                return;
            }
            mancante.setVisibility(GONE);
            card.setVisibility(VISIBLE);
            // tutte le spese, anche quelle inserite prima di questa versione, seguono la nuova logica
            long[] r = calcola(d.movimenti, d.io.getId(), 0L);
            buoni = r[0];
            soldi = r[1];
            int iSx = d.membri.isEmpty() || d.membri.get(0).getId().equals(d.io.getId()) ? 0 : 1;
            Member sx = d.membri.isEmpty() ? d.io : d.membri.get(0);
            Member dx = d.membri.size() > 1 ? d.membri.get(1) : d.altro;
            bilancia.persone(sx.getName(), dx.getName(), 0xFF5E5CE6, 0xFFFF375F);
            aggiornaContenuti(false);
        }

        private void aggiornaContenuti(boolean dopoCambio) {
            Aggiorna d = ultimo;
            if (d == null || d.altro == null) return;
            long v = modoBuoni ? buoni : soldi;
            String altro = d.altro.getName();
            chi.setText(v > 0 ? Ui.s(lc, "ti_deve", altro) : v < 0 ? Ui.s(lc, "devi_a", altro) : Ui.s(lc, "siete_pari"));
            if (modoBuoni) {
                spiega.setText(v > 0 ? Ui.s(lc, "saldi_buoni_ti_deve", altro, buoniTesto(lc, v))
                        : v < 0 ? Ui.s(lc, "saldi_buoni_devi", buoniTesto(lc, v)) : Ui.s(lc, "saldi_buoni_pari"));
            } else {
                spiega.setText(Ui.s(lc, "saldi_soldi_spiega"));
            }
            final int colore = v > 0 ? VERDE : v < 0 ? ARANCIO : Ui.TESTO;
            valore.setTextColor(colore);
            conta(Math.abs(modoBuoni ? v / 2.0 : v / 100.0));

            // bilancia: a sinistra il primo membro; scende il piatto di chi deve ricevere
            boolean ioSinistra = d.membri.isEmpty() || d.membri.get(0).getId().equals(d.io.getId());
            double perSinistra = ioSinistra ? v : -v;
            double scala = modoBuoni ? 16.0 : 5000.0; // 8 buoni o 50 euro = inclinazione massima
            bilancia.peso((float) (perSinistra / scala));

            // riepilogo
            tileB.setText(riepilogo(Ui.s(lc, "saldi_buoni_titolo"), buoni, true, altro));
            tileS.setText(riepilogo(Ui.s(lc, "saldi_soldi_titolo"), soldi, false, altro));
            tileB.setBackground(Ui.sfondo(getContext(), modoBuoni ? 0xFF3A3A3C : 0xFF2C2C2E, 10));
            tileS.setBackground(Ui.sfondo(getContext(), !modoBuoni ? 0xFF3A3A3C : 0xFF2C2C2E, 10));
            segB.setTextColor(modoBuoni ? Ui.TESTO : Ui.TESTO_2);
            segS.setTextColor(!modoBuoni ? Ui.TESTO : Ui.TESTO_2);

            saldato.setText(Ui.s(lc, modoBuoni ? "saldi_ripagato" : "segna_saldato"));
            boolean mostraSaldato = modoBuoni ? buoni != 0 : soldi != 0;
            if (mostraSaldato && saldato.getVisibility() != VISIBLE) {
                saldato.setVisibility(VISIBLE);
                saldato.setAlpha(0f);
                saldato.setTranslationY(Ui.dp(getContext(), 8));
                saldato.animate().alpha(1f).translationY(0).setDuration(240).start();
            } else if (!mostraSaldato) {
                saldato.setVisibility(GONE);
            }
        }

        private CharSequence riepilogo(String titolo, long v, boolean inBuoni, String altro) {
            String importo = v == 0 ? Ui.s(lc, "siete_pari")
                    : (inBuoni ? buoniTesto(lc, v) : ModelKt.formatEuro(Math.abs(v)));
            String verso = v > 0 ? "↓ " : v < 0 ? "↑ " : "";
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            sb.append(titolo.toUpperCase(Locale.getDefault()));
            sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.TESTO_2), 0, sb.length(), 0);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.82f), 0, sb.length(), 0);
            sb.append('\n');
            int da = sb.length();
            sb.append(verso).append(importo);
            sb.setSpan(new android.text.style.ForegroundColorSpan(v > 0 ? VERDE : v < 0 ? ARANCIO : Ui.TESTO), da, sb.length(), 0);
            return sb;
        }

        /** Il numero grande scorre fino al nuovo valore. */
        private void conta(final double fine) {
            final boolean inBuoni = modoBuoni;
            if (numero != null) numero.cancel();
            double inizio = Double.isNaN(mostrato) || mostratoBuoni != inBuoni ? 0 : mostrato;
            mostratoBuoni = inBuoni;
            if (Math.abs(inizio - fine) < 0.001) {
                mostrato = fine;
                valore.setText(formato(fine, inBuoni));
                return;
            }
            numero = android.animation.ValueAnimator.ofFloat((float) inizio, (float) fine);
            numero.setDuration(650);
            numero.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
            numero.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    float x = (Float) a.getAnimatedValue();
                    mostrato = x;
                    valore.setText(formato(x, inBuoni));
                }
            });
            numero.start();
        }

        private String formato(double x, boolean inBuoni) {
            if (inBuoni) {
                double arr = Math.round(x * 2) / 2.0;
                String n = arr == Math.rint(arr) ? String.valueOf((long) arr) : String.format(Locale.ITALY, "%.1f", arr);
                return Ui.s(lc, arr == 1 ? "saldi_buono_1" : "saldi_buoni_n", n);
            }
            return ModelKt.formatEuro(Math.round(x * 100));
        }

        /** Chi ha finito i buoni ripaga in soldi tutto il debito di buoni: si azzera il saldo dei buoni. */
        private void confermaBuoni(final Context ctx, final Aggiorna d, final long mezzi) {
            final Member debitore = mezzi > 0 ? d.altro : d.io, creditore = mezzi > 0 ? d.io : d.altro;
            final long n = Math.abs(mezzi);
            long valore = creditore.getVoucherCents() > 0 ? creditore.getVoucherCents()
                    : debitore.getVoucherCents() > 0 ? debitore.getVoucherCents() : 800;
            final long proposta = Math.round(n * valore / 2.0);
            final Dialog dlg = new Dialog(ctx);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(VERTICAL);
            int p = Ui.dp(ctx, 20);
            box.setPadding(p, p, p, Ui.dp(ctx, 12));
            box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
            box.addView(Ui.testo(ctx, Ui.s(lc, "saldi_ripagato"), 17, Ui.TESTO, true));
            TextView msg = Ui.testo(ctx, Ui.s(lc, "saldi_ripagato_testo", debitore.getName(), buoniTesto(lc, n), creditore.getName()),
                    13, Ui.TESTO_2, false);
            msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 4));
            box.addView(msg);
            TextView et = sx(Ui.testo(ctx, Ui.s(lc, "saldi_ripagato_importo").toUpperCase(Locale.getDefault()), 12, Ui.TESTO_2, false));
            et.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), 0, Ui.dp(ctx, 6));
            box.addView(et);
            final android.widget.EditText importo = new android.widget.EditText(ctx);
            importo.setText(ModelKt.centsToInput(proposta));
            importo.setSingleLine(true);
            importo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            importo.setTextColor(Ui.TESTO);
            importo.setTextSize(17);
            importo.setBackground(Ui.sfondo(ctx, 0xFF1C1C1E, 10));
            int pp = Ui.dp(ctx, 12);
            importo.setPadding(pp, Ui.dp(ctx, 11), pp, Ui.dp(ctx, 11));
            box.addView(importo, new LayoutParams(-1, -2));
            TextView nota = sx(Ui.testo(ctx, Ui.s(lc, "saldi_ripagato_nota", ModelKt.formatEuro(valore)), 12, Ui.TESTO_2, false));
            nota.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
            box.addView(nota);
            TextView si = Ui.testo(ctx, Ui.s(lc, "saldi_ripagato"), 17, Color.WHITE, true);
            si.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
            si.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
            si.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Long cent = ModelKt.parseCents(String.valueOf(importo.getText()));
                    if (cent == null) {
                        android.widget.Toast.makeText(ctx, Ui.s(lc, "agg_extra_errato"), android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Calendar oggi = Calendar.getInstance();
                    oggi.set(Calendar.HOUR_OF_DAY, 0);
                    oggi.set(Calendar.MINUTE, 0);
                    oggi.set(Calendar.SECOND, 0);
                    oggi.set(Calendar.MILLISECOND, 0);
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("type", "SETTLEMENT");
                    m.put("payerId", debitore.getId());
                    m.put("payerName", debitore.getName());
                    m.put("receiverName", creditore.getName());
                    m.put("vouchers", 0);
                    m.put("voucherCents", 0L);
                    m.put("extraCents", cent);
                    m.put("split", "OTHER_ONLY");
                    m.put("note", SEGNO_BUONI + n);
                    m.put("dateMillis", oggi.getTimeInMillis());
                    m.put("createdAt", System.currentTimeMillis());
                    m.put("createdBy", d.io.getId());
                    FirebaseFirestore.getInstance().collection("groups").document(d.gruppo)
                            .collection("movements").document().set(m);
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    dlg.dismiss();
                }
            });
            box.addView(si, new LayoutParams(-1, -2));
            TextView no = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
            no.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
            no.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            });
            box.addView(no);
            dlg.setContentView(box);
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
                w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            dlg.show();
        }

        /** Rimborso in soldi: chi e' in debito ha dato la cifra all'altro. */
        private void conferma(final Context ctx, final Aggiorna d, final long soldi) {
            final Member debitore = soldi > 0 ? d.altro : d.io, creditore = soldi > 0 ? d.io : d.altro;
            final long importo = Math.abs(soldi);
            final Dialog dlg = new Dialog(ctx);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(VERTICAL);
            int p = Ui.dp(ctx, 20);
            box.setPadding(p, p, p, Ui.dp(ctx, 12));
            box.setBackground(Ui.sfondo(ctx, Ui.SUPERFICIE, 16));
            box.addView(Ui.testo(ctx, Ui.s(lc, "segna_saldato"), 17, Ui.TESTO, true));
            TextView msg = Ui.testo(ctx, Ui.s(lc, "saldi_conferma", debitore.getName(), ModelKt.formatEuro(importo), creditore.getName()),
                    13, Ui.TESTO_2, false);
            msg.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 16));
            box.addView(msg);
            TextView si = Ui.testo(ctx, Ui.s(lc, "segna_saldato"), 17, Color.WHITE, true);
            si.setBackground(Ui.sfondo(ctx, Ui.ACCENTO, 14));
            si.setPadding(0, Ui.dp(ctx, 13), 0, Ui.dp(ctx, 13));
            si.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Calendar oggi = Calendar.getInstance();
                    oggi.set(Calendar.HOUR_OF_DAY, 0);
                    oggi.set(Calendar.MINUTE, 0);
                    oggi.set(Calendar.SECOND, 0);
                    oggi.set(Calendar.MILLISECOND, 0);
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("type", "SETTLEMENT");
                    m.put("payerId", debitore.getId());
                    m.put("payerName", debitore.getName());
                    m.put("receiverName", creditore.getName());
                    m.put("vouchers", 0);
                    m.put("voucherCents", 0L);
                    m.put("extraCents", importo);
                    m.put("split", "OTHER_ONLY");
                    m.put("note", "");
                    m.put("dateMillis", oggi.getTimeInMillis());
                    m.put("createdAt", System.currentTimeMillis());
                    m.put("createdBy", d.io.getId());
                    FirebaseFirestore.getInstance().collection("groups").document(d.gruppo)
                            .collection("movements").document().set(m);
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    dlg.dismiss();
                }
            });
            box.addView(si, new LayoutParams(-1, -2));
            TextView no = Ui.testo(ctx, Ui.s(lc, "annulla"), 17, Ui.ACCENTO, false);
            no.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6));
            no.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                }
            });
            box.addView(no);
            dlg.setContentView(box);
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
            }
            dlg.show();
        }

        private static LinearLayout carta(Context c) {
            LinearLayout l = new LinearLayout(c);
            l.setOrientation(VERTICAL);
            l.setBackground(Ui.sfondo(c, 0xFF1C1C1E, 16));
            int p = Ui.dp(c, 16);
            l.setPadding(p, p, p, Ui.dp(c, 16));
            l.setClipChildren(false);
            return l;
        }

        private static LayoutParams margini(Context c, int sopra) {
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.leftMargin = Ui.dp(c, 16);
            lp.rightMargin = Ui.dp(c, 16);
            lp.topMargin = Ui.dp(c, sopra);
            return lp;
        }

        private static TextView sx(TextView t) {
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            return t;
        }
    }
}
