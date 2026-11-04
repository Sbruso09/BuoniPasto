package com.mario.buonipasto.ai;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mario.buonipasto.Lingua;
import com.mario.buonipasto.MainActivity;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import kotlinx.coroutines.flow.MutableStateFlow;

/**
 * "Unisci due diete": l'utente sceglie due PDF (una dieta per persona) e l'assistente
 * costruisce in chat una dieta unica, con alimenti compatibili e le grammature di ciascuno.
 * Usa la stessa chat, lo stesso indicatore di attesa e la stessa notifica dell'assistente.
 */
public final class DueDiete {
    private static final int MAX_BYTE = 15 * 1024 * 1024;

    private DueDiete() {}

    /** Chiamato all'inizio di Motore.avvia: true se la domanda e' il suggerimento "Unisci due diete". */
    public static boolean intercetta(Context c, String domanda) {
        if (domanda == null || !domanda.trim().equals(Ui.s(c, "chip_due_diete_t").trim())) return false;
        if (!ChiaveAi.complete(c)) {
            Toast.makeText(c, Ui.s(c, "due_diete_chiavi"), Toast.LENGTH_LONG).show();
            return true;
        }
        if (stato().getValue() != null && ((StatoAi) stato().getValue()).getInCorso()) return true;
        Intent i = new Intent(c, Scelta.class);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static MutableStateFlow<StatoAi> stato() {
        return (MutableStateFlow<StatoAi>) (Object) Motore.INSTANCE.getStato();
    }

    static String nome(Context c, Uri uri) {
        String n = null;
        try {
            Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null) {
                if (cur.moveToFirst()) n = cur.getString(0);
                cur.close();
            }
        } catch (RuntimeException ignored) {
        }
        if (n == null) n = "dieta";
        if (n.toLowerCase().endsWith(".pdf")) n = n.substring(0, n.length() - 4);
        return n;
    }

    static byte[] leggi(Context c, Uri uri) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("file");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > MAX_BYTE) {
                in.close();
                throw new IllegalStateException("PDF > 15 MB");
            }
        }
        in.close();
        return b.toByteArray();
    }

    // ------------------------------------------------------------------ lavoro

    static void avvia(final Context app, final String nomeA, final byte[] pdfA, final String nomeB, final byte[] pdfB) {
        final MutableStateFlow<StatoAi> flusso = stato();
        StatoAi ora = flusso.getValue();
        if (ora.getInCorso()) return;
        final Context lc = Lingua.INSTANCE.contesto(app);
        final long mio = System.currentTimeMillis();
        String domanda = Ui.s(lc, "due_diete_messaggio", nomeA, nomeB);
        ChatStorico.INSTANCE.aggiungi(app, new ChatStorico.Messaggio(ChatStorico.UTENTE, domanda, mio));
        flusso.setValue(new StatoAi(true, "", Ui.s(lc, "due_diete_fase"), mio, ora.getVersione() + 1));
        Servizio.Companion.avvia(app);

        new Thread(new Runnable() {
            @Override
            public void run() {
                ChatStorico.Messaggio risposta;
                try {
                    risposta = new ChatStorico.Messaggio(ChatStorico.AI, chiedi(app, lc, nomeA, pdfA, nomeB, pdfB, mio), System.currentTimeMillis());
                } catch (Gemini.Errore e) {
                    risposta = e.tipo == Gemini.Errore.ANNULLATA ? null
                            : new ChatStorico.Messaggio(ChatStorico.ERRORE, e.getMessage(), System.currentTimeMillis());
                } catch (Throwable e) {
                    risposta = new ChatStorico.Messaggio(ChatStorico.ERRORE, Ui.s(lc, "due_diete_errore", String.valueOf(e.getMessage() != null ? e.getMessage() : e)), System.currentTimeMillis());
                }
                synchronized (Motore.INSTANCE) {
                    StatoAi s = flusso.getValue();
                    if (!s.getInCorso() || s.getInizio() != mio) return; // fermata con il tasto stop
                    if (risposta != null) ChatStorico.INSTANCE.aggiungi(app, risposta);
                    flusso.setValue(new StatoAi(false, "", "", 0L, s.getVersione() + 1));
                }
                Servizio.Companion.ferma(app);
                if (risposta != null && !Motore.INSTANCE.getChatVisibile()) notifica(app, lc, risposta);
            }
        }).start();
    }

    private static String istruzioni(String nomeA, String nomeB) {
        String lingua = Lingua.INSTANCE.inglese() ? "Rispondi SEMPRE in inglese (English)." : "Rispondi in italiano.";
        return "Sei l'assistente di Buoni Pasto. Ricevi DUE diete in PDF, ciascuna per una persona diversa:\n"
                + "- Persona A: \"" + nomeA + "\"\n- Persona B: \"" + nomeB + "\"\n\n"
                + "Costruisci UNA DIETA UNICA settimanale per le due persone, da cucinare insieme:\n"
                + "1. Mantieni la struttura dei pasti (colazione, spuntini, pranzo, cena) e i giorni delle diete.\n"
                + "2. In ogni pasto scegli piatti e alimenti compatibili con ENTRAMBE le diete, così si prepara una cosa sola.\n"
                + "3. Rispetta le quantità di ciascuno: scrivi le grammature separate, es. \"pasta integrale (A 80 g · B 60 g)\".\n"
                + "4. Se un alimento c'è in una sola dieta, sostituiscilo con quello più simile presente nell'altra (o un equivalente "
                + "della stessa categoria e con porzione equivalente) e segnalalo con ↔.\n"
                + "5. Non aggiungere alimenti che nessuna delle due diete prevede e non cambiare le calorie in modo evidente.\n"
                + "6. In fondo: **Differenze principali** (2-5 punti) e un promemoria breve che per cambi importanti va sentito "
                + "il nutrizionista.\n"
                + "Formattazione: titolo in **grassetto** per ogni giorno ed elenchi con '- ' per i pasti. Niente tabelle. "
                + lingua;
    }

    private static JsonObject parteFile(String mime, byte[] dati) {
        JsonObject inline = new JsonObject();
        inline.addProperty("mimeType", mime);
        inline.addProperty("data", Base64.encodeToString(dati, Base64.NO_WRAP));
        JsonObject p = new JsonObject();
        p.add("inlineData", inline);
        return p;
    }

    private static JsonObject parteTesto(String t) {
        JsonObject p = new JsonObject();
        p.addProperty("text", t);
        return p;
    }

    private static JsonArray conversazione(JsonArray parti) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.add("parts", parti);
        JsonArray a = new JsonArray();
        a.add(m);
        return a;
    }

    private static String chiedi(final Context app, Context lc, String nomeA, byte[] pdfA, String nomeB, byte[] pdfB,
                                 final long mio) throws Exception {
        String chiave = ChiaveAi.leggi(app), chiaveGroq = ChiaveAi.leggi(app, ChiaveAi.GROQ);
        if (chiave == null || chiaveGroq == null) throw new IllegalStateException(Ui.s(lc, "due_diete_chiavi"));
        String istr = istruzioni(nomeA, nomeB);
        String richiesta = "Ecco le due diete: la prima è della persona A, la seconda della persona B. Crea la dieta unica.";

        final MutableStateFlow<StatoAi> flusso = stato();
        Gemini.Ascolto ascolto = new Gemini.Ascolto() {
            @Override
            public void testo(String finora) {
                synchronized (Motore.INSTANCE) {
                    StatoAi s = flusso.getValue();
                    if (s.getInCorso() && s.getInizio() == mio) {
                        flusso.setValue(new StatoAi(true, finora, s.getFase(), s.getInizio(), s.getVersione()));
                    }
                }
            }

            @Override
            public void stato(int fase, String dettaglio) {
            }
        };

        Gemini g = new Gemini(new Gemini.Http(), chiave);
        g.usaModelli(Motore.INSTANCE.modelli(app));
        g.messaggi = Motore.INSTANCE.messaggi(lc);
        g.ascolto = ascolto;
        try {
            JsonArray parti = new JsonArray();
            parti.add(parteTesto("Dieta A: " + nomeA));
            parti.add(parteFile("application/pdf", pdfA));
            parti.add(parteTesto("Dieta B: " + nomeB));
            parti.add(parteFile("application/pdf", pdfB));
            parti.add(parteTesto(richiesta));
            return g.conversa(istr, conversazione(parti), null, null);
        } catch (Gemini.Errore eg) {
            if (eg.tipo == Gemini.Errore.CHIAVE || eg.tipo == Gemini.Errore.ANNULLATA) throw eg;
            ascolto.testo("");
            // riserva: Groq non legge i PDF, quindi riceve le prime pagine come immagini (massimo 3)
            Groq q = new Groq(new Groq.Http(), chiaveGroq);
            q.messaggi = g.messaggi;
            q.erroreChiave = Ui.s(lc, "ai_err_chiave_groq");
            q.ascolto = ascolto;
            try {
                List<String> a = pagine(app, pdfA, 2), b = pagine(app, pdfB, 1);
                JsonArray parti = new JsonArray();
                for (String x : a) parti.add(parteFile("image/jpeg", Base64.decode(x, Base64.NO_WRAP)));
                for (String x : b) parti.add(parteFile("image/jpeg", Base64.decode(x, Base64.NO_WRAP)));
                parti.add(parteTesto("Le prime " + a.size() + " immagini sono la dieta A (" + nomeA + "), l'ultima è la dieta B ("
                        + nomeB + "). " + richiesta));
                return q.conversa(istr, conversazione(parti), null, null, Groq.FOTO);
            } finally {
                UsoAi.aggiungi(app, 0, q.richieste);
            }
        } finally {
            UsoAi.aggiungi(app, g.richieste, 0);
        }
    }

    /** Prime pagine di un PDF come JPEG in base64. */
    static List<String> pagine(Context c, byte[] pdf, int quante) throws Exception {
        File f = new File(c.getCacheDir(), "dieta_" + System.nanoTime() + ".pdf");
        FileOutputStream out = new FileOutputStream(f);
        out.write(pdf);
        out.close();
        List<String> l = new ArrayList<String>();
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer r = new PdfRenderer(pfd);
        try {
            for (int i = 0; i < Math.min(quante, r.getPageCount()); i++) {
                PdfRenderer.Page p = r.openPage(i);
                int w = 1240, h = Math.min(2400, (int) (1240f * p.getHeight() / p.getWidth()));
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, b);
                bmp.recycle();
                l.add(Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP));
            }
        } finally {
            r.close();
            pfd.close();
            f.delete();
        }
        return l;
    }

    private static void notifica(Context app, Context c, ChatStorico.Messaggio m) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("assistente_risposte", Ui.s(c, "notif_ai_canale"),
                    NotificationManager.IMPORTANCE_HIGH));
            String testo = m.getTesto().replace("**", "").replaceAll("(?m)^- ", "• ").trim();
            Intent apri = new Intent(c, MainActivity.class);
            apri.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            apri.putExtra("apri_assistente", true);
            PendingIntent pi = PendingIntent.getActivity(c, 5252, apri, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            int icona = c.getResources().getIdentifier("ic_tab_chat", "drawable", c.getPackageName());
            boolean errore = ChatStorico.ERRORE.equals(m.getRuolo());
            Notification n = new NotificationCompat.Builder(c, "assistente_risposte")
                    .setSmallIcon(icona)
                    .setContentTitle(Ui.s(c, errore ? "notif_ai_errore" : "notif_ai_risposta"))
                    .setContentText(testo)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(testo.length() > 600 ? testo.substring(0, 600) + "…" : testo))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            nm.notify(5252, n);
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ scelta dei due PDF

    /** Schermata invisibile che apre la scelta dei file e passa i due PDF all'assistente. */
    public static final class Scelta extends Activity {
        private final ArrayList<Uri> scelti = new ArrayList<Uri>();

        @Override
        protected void onCreate(Bundle stato) {
            super.onCreate(stato);
            if (stato != null) {
                ArrayList<String> s = stato.getStringArrayList("scelti");
                if (s != null) for (String x : s) scelti.add(Uri.parse(x));
                return; // la scelta dei file e' gia' aperta
            }
            Toast.makeText(this, Ui.s(this, "due_diete_scegli"), Toast.LENGTH_LONG).show();
            apri(true);
        }

        @Override
        protected void onSaveInstanceState(Bundle out) {
            super.onSaveInstanceState(out);
            ArrayList<String> s = new ArrayList<String>();
            for (Uri u : scelti) s.add(u.toString());
            out.putStringArrayList("scelti", s);
        }

        private void apri(boolean multipla) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multipla);
            try {
                startActivityForResult(i, 7);
            } catch (RuntimeException e) {
                finish();
            }
        }

        @Override
        protected void onActivityResult(int richiesta, int esito, Intent dati) {
            super.onActivityResult(richiesta, esito, dati);
            if (esito != RESULT_OK || dati == null) {
                finish();
                return;
            }
            ClipData clip = dati.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) aggiungi(clip.getItemAt(i).getUri());
            } else if (dati.getData() != null) {
                aggiungi(dati.getData());
            }
            if (scelti.size() < 2) {
                Toast.makeText(this, Ui.s(this, "due_diete_seconda"), Toast.LENGTH_LONG).show();
                apri(false);
                return;
            }
            try {
                Uri a = scelti.get(0), b = scelti.get(1);
                avvia(getApplicationContext(), nome(this, a), leggi(this, a), nome(this, b), leggi(this, b));
            } catch (Exception e) {
                Toast.makeText(this, Ui.s(this, "due_diete_errore", String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            }
            finish();
        }

        private void aggiungi(Uri u) {
            if (u != null && !scelti.contains(u)) scelti.add(u);
        }
    }
}
