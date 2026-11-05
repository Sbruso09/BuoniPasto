package com.mario.buonipasto.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.mario.buonipasto.data.DatiApp;
import com.mario.buonipasto.data.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Dati condivisi dalle due persone dello stesso gruppo:
 *  - la dieta (menu e alimenti per la lista): chi carica il PDF lo carica per tutti e due;
 *  - le chiavi AI (Gemini e Groq): se le inserisce uno, le usa anche l'altro.
 * Sono salvati come documenti speciali nel gruppo (senza "payerId", quindi la lista delle spese li ignora);
 * le chiavi sono cifrate con una chiave ricavata dal codice del gruppo.
 */
public final class Condivisi {
    private static final String DOC_DIETA = "_condiviso_dieta", DOC_CHIAVI = "_condiviso_chiavi";
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static Context app;
    private static String gruppo;
    private static ListenerRegistration regDieta, regChiavi, regRegole;
    private static final String DOC_REGOLE = "_condiviso_regole";
    private static final java.util.List<Runnable> ascoltiPassaggio = new java.util.ArrayList<Runnable>();
    private static boolean avviato;

    private Condivisi() {}

    /** Chiamato all'apertura dell'app: aspetta la sessione del gruppo e si mette in ascolto. */
    public static void avvia(Context c) {
        app = c.getApplicationContext();
        if (avviato) return;
        avviato = true;
        main.post(controllo);
    }

    private static final Runnable controllo = new Runnable() {
        @Override
        public void run() {
            try {
                Session s = DatiApp.INSTANCE.getSession();
                String codice = s != null ? s.getGroupCode() : null;
                if (codice != null && !codice.equals(gruppo)) collega(codice);
                if (codice != null) inviaChiaviSeCambiate();
                if (codice != null) Stima.calibra(app); // spese reali per tarare la stima della lista
            } catch (Throwable ignored) {
            }
            main.postDelayed(this, 4000);
        }
    };

    private static DocumentReference doc(String codice, String id) {
        return FirebaseFirestore.getInstance().collection("groups").document(codice).collection("movements").document(id);
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences("condivisi", Context.MODE_PRIVATE);
    }

    private static void collega(final String codice) {
        if (regDieta != null) regDieta.remove();
        if (regChiavi != null) regChiavi.remove();
        if (regRegole != null) regRegole.remove();
        gruppo = codice;
        regRegole = doc(codice, DOC_REGOLE).addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot d, FirebaseFirestoreException e) {
                if (e != null || d == null) return;
                Long p = d.exists() ? d.getLong("passaggio") : null;
                if (p != null) {
                    if (p != prefs().getLong("passaggio_" + codice, -1)) {
                        prefs().edit().putLong("passaggio_" + codice, p).apply();
                        avvisaPassaggio();
                    }
                } else if (!d.getMetadata().isFromCache()) {
                    // primo telefono aggiornato del gruppo: da adesso buoni e soldi hanno saldi separati
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("passaggio", System.currentTimeMillis());
                    doc(codice, DOC_REGOLE).set(m);
                }
            }
        });
        regDieta = doc(codice, DOC_DIETA).addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot d, FirebaseFirestoreException e) {
                if (e == null && d != null && d.exists()) riceviDieta(d);
            }
        });
        regChiavi = doc(codice, DOC_CHIAVI).addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot d, FirebaseFirestoreException e) {
                if (e == null && d != null) riceviChiavi(d);
            }
        });
        // dieta gia' caricata qui ma non ancora nel gruppo (es. caricata prima di questo aggiornamento)
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (codice.equals(gruppo) && prefs().getLong("dieta_ts_" + codice, 0) == 0
                        && MenuDieta.vociSalvate(app) != null) inviaDieta(app);
            }
        }, 6000);
    }

    /** Momento da cui buoni e soldi hanno saldi separati (prima: conto unico). Long.MAX_VALUE se non ancora noto. */
    static long passaggio(String codice) {
        if (app == null || codice == null) return Long.MAX_VALUE;
        return prefs().getLong("passaggio_" + codice, Long.MAX_VALUE);
    }

    static void ascoltaPassaggio(Runnable r) {
        synchronized (ascoltiPassaggio) {
            ascoltiPassaggio.clear(); // un solo pannello alla volta
            ascoltiPassaggio.add(r);
        }
    }

    private static void avvisaPassaggio() {
        main.post(new Runnable() {
            @Override
            public void run() {
                java.util.List<Runnable> l;
                synchronized (ascoltiPassaggio) {
                    l = new java.util.ArrayList<Runnable>(ascoltiPassaggio);
                }
                for (Runnable r : l) r.run();
            }
        });
    }

    // ------------------------------------------------------------------ dieta

    /** Dopo aver letto un PDF: lo rende disponibile anche all'altra persona del gruppo. */
    static void inviaDieta(Context c) {
        if (app == null) app = c.getApplicationContext();
        Session s = DatiApp.INSTANCE.getSession();
        if (s == null) return;
        SharedPreferences mp = MenuDieta.prefs(c);
        String json = mp.getString("json", null), voci = mp.getString("voci", null);
        if (json == null) return;
        long ts = System.currentTimeMillis();
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("json", json);
        m.put("voci", voci == null ? "" : voci);
        m.put("ts", ts);
        m.put("da", s.getName());
        prefs().edit().putLong("dieta_ts_" + s.getGroupCode(), ts).apply();
        doc(s.getGroupCode(), DOC_DIETA).set(m);
    }

    private static void riceviDieta(DocumentSnapshot d) {
        Long ts = d.getLong("ts");
        String json = d.getString("json"), voci = d.getString("voci");
        if (ts == null || json == null || gruppo == null) return;
        if (ts <= prefs().getLong("dieta_ts_" + gruppo, 0)) return; // gia' qui (o l'ho inviata io)
        prefs().edit().putLong("dieta_ts_" + gruppo, ts).apply();
        SharedPreferences.Editor e = MenuDieta.prefs(app).edit().putString("json", json).remove("json_it").remove("json_en");
        if (voci != null && !voci.isEmpty()) e.putString("voci", voci);
        else e.remove("voci");
        e.apply();
        MenuDieta.dietaArrivata();
    }

    // ------------------------------------------------------------------ chiavi AI

    private static String impronta(String a, String b) {
        return (a == null ? "" : a) + "\n" + (b == null ? "" : b);
    }

    /** Se le chiavi sono cambiate qui (inserite, cambiate o tolte) le invia al gruppo. */
    private static void inviaChiaviSeCambiate() {
        String g = ChiaveAi.leggi(app), q = ChiaveAi.leggi(app, ChiaveAi.GROQ);
        String ora = impronta(g, q);
        String k = "chiavi_" + gruppo;
        if (!prefs().contains(k)) {
            // prima volta in questo gruppo: invio solo se ho qualcosa (non cancello quelle dell'altro)
            if (g == null && q == null) return;
        } else if (ora.equals(prefs().getString(k, ""))) {
            return;
        }
        prefs().edit().putString(k, ora).apply();
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("gemini", g == null ? "" : cifra(g));
        m.put("groq", q == null ? "" : cifra(q));
        m.put("ts", System.currentTimeMillis());
        doc(gruppo, DOC_CHIAVI).set(m);
    }

    private static void riceviChiavi(DocumentSnapshot d) {
        if (gruppo == null) return;
        String k = "chiavi_" + gruppo;
        if (!d.exists()) return;
        String g = decifra(d.getString("gemini")), q = decifra(d.getString("groq"));
        String remoto = impronta(g, q);
        if (remoto.equals(prefs().getString(k, null))) return;
        String gl = ChiaveAi.leggi(app), ql = ChiaveAi.leggi(app, ChiaveAi.GROQ);
        if (!prefs().contains(k) && gl != null && ql != null && (g == null || q == null)) return; // le mie sono piu' complete
        if (g != null) ChiaveAi.salva(app, g);
        else if (prefs().contains(k)) ChiaveAi.rimuovi(app);
        if (q != null) ChiaveAi.salva(app, q, ChiaveAi.GROQ);
        else if (prefs().contains(k)) ChiaveAi.rimuovi(app, ChiaveAi.GROQ);
        prefs().edit().putString(k, impronta(ChiaveAi.leggi(app), ChiaveAi.leggi(app, ChiaveAi.GROQ))).apply();
    }

    private static SecretKeySpec chiaveGruppo() throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(("buonipasto-chiavi:" + gruppo).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(h, "AES");
    }

    private static String cifra(String testo) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chiaveGruppo(), new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(testo.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static String decifra(String s) {
        if (s == null || s.isEmpty() || s.indexOf(':') < 0) return null;
        try {
            String[] p = s.split(":", 2);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chiaveGruppo(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
            String t = new String(c.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), StandardCharsets.UTF_8).trim();
            return t.isEmpty() ? null : t;
        } catch (Exception e) {
            return null;
        }
    }
}
