package com.mario.buonipasto.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mario.buonipasto.Lingua
import com.mario.buonipasto.MainActivity
import com.mario.buonipasto.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/** Stato della domanda in corso, osservato dalla chat. */
data class StatoAi(
    val inCorso: Boolean = false,
    val parziale: String = "",
    val fase: String = "",
    val inizio: Long = 0L,
    /** Cambia ogni volta che la chat salvata cambia (nuova risposta, chat cancellata). */
    val versione: Int = 0,
)

/**
 * Esegue le domande indipendentemente dalla schermata: la risposta arriva anche se cambi
 * pagina o chiudi l'app (un servizio in primo piano tiene vivo il lavoro). Se non stai
 * guardando la chat arriva la notifica "Buoni Pasto ha risposto".
 * Gemini e' il servizio principale; quando finisce le richieste gratuite si passa a Groq.
 */
object Motore {
    const val K_MODELLO = "modello" // "veloce" (Flash-Lite) o "intelligente" (Flash)
    private const val CANALE_RISPOSTE = "assistente_risposte"
    const val ID_RISPOSTA = 5252

    private val esecutore = Executors.newCachedThreadPool()
    private val _stato = MutableStateFlow(StatoAi())
    val stato: StateFlow<StatoAi> = _stato

    @Volatile private var generazione = 0
    @Volatile private var gemini: Gemini? = null
    @Volatile private var groq: Groq? = null
    @Volatile var chatVisibile = false

    fun chiaviComplete(c: Context) = ChiaveAi.complete(c)

    private fun aggiorna(mia: Int, f: (StatoAi) -> StatoAi) {
        synchronized(this) {
            if (mia == generazione && _stato.value.inCorso) _stato.value = f(_stato.value)
        }
    }

    fun chatCambiata() {
        synchronized(this) { _stato.value = _stato.value.copy(versione = _stato.value.versione + 1) }
    }

    /** Messaggi d'errore nella lingua dell'app. */
    fun messaggi(lc: Context): Gemini.Messaggi = Gemini.Messaggi().apply {
        rete = lc.getString(R.string.ai_err_rete)
        vuota = lc.getString(R.string.ai_err_vuota)
        nessuna = lc.getString(R.string.ai_err_nessuna)
        passaggi = lc.getString(R.string.ai_err_passaggi)
        chiave = lc.getString(R.string.ai_err_chiave)
        quota = lc.getString(R.string.ai_err_quota)
        servizio = lc.getString(R.string.ai_err_servizio)
        errore = lc.getString(R.string.ai_err_generico)
    }

    fun modelli(c: Context): Array<String> =
        if (c.getSharedPreferences("ai", Context.MODE_PRIVATE).getString(K_MODELLO, "veloce") == "intelligente")
            Gemini.MODELLI_INTELLIGENTE else Gemini.MODELLI_VELOCE

    /** Invia una domanda: il messaggio dell'utente viene salvato subito, la risposta quando arriva. */
    fun avvia(contesto: Context, domanda: String) {
        if (DueDiete.intercetta(contesto, domanda)) return // "unisci due diete": sceglie i PDF e li unisce
        val ctx = contesto.applicationContext
        val chiave = ChiaveAi.leggi(ctx) ?: return
        val chiaveGroq = ChiaveAi.leggi(ctx, ChiaveAi.GROQ) ?: return
        val mia: Int
        synchronized(this) {
            if (_stato.value.inCorso) return
            mia = ++generazione
            ChatStorico.aggiungi(ctx, ChatStorico.Messaggio(ChatStorico.UTENTE, domanda))
            _stato.value = StatoAi(true, "", "", System.currentTimeMillis(), _stato.value.versione + 1)
        }
        Servizio.avvia(ctx)
        val conversazione = ChatStorico.leggi(ctx)
        esecutore.execute {
            val risposta: ChatStorico.Messaggio? = try {
                ChatStorico.Messaggio(ChatStorico.AI, esegui(ctx, conversazione, domanda, chiave, chiaveGroq, mia))
            } catch (e: Gemini.Errore) {
                if (e.tipo == Gemini.Errore.ANNULLATA) null else ChatStorico.Messaggio(ChatStorico.ERRORE, e.message.orEmpty())
            } catch (e: Throwable) {
                ChatStorico.Messaggio(ChatStorico.ERRORE, Lingua.contesto(ctx).getString(R.string.ai_err_storto, e.toString()))
            }
            fine(ctx, mia, risposta)
        }
    }

    private fun fine(ctx: Context, mia: Int, risposta: ChatStorico.Messaggio?) {
        synchronized(this) {
            if (mia != generazione) return // fermata nel frattempo
            if (risposta != null) ChatStorico.aggiungi(ctx, risposta)
            _stato.value = StatoAi(versione = _stato.value.versione + 1)
            gemini = null
            groq = null
        }
        Servizio.ferma(ctx)
        if (risposta != null && !chatVisibile) notifica(ctx, risposta)
    }

    fun ferma(c: Context) {
        val g: Gemini?
        val q: Groq?
        synchronized(this) {
            if (!_stato.value.inCorso) return
            generazione++
            g = gemini
            q = groq
            gemini = null
            groq = null
            _stato.value = StatoAi(versione = _stato.value.versione + 1)
        }
        esecutore.execute {
            g?.annulla()
            q?.annulla()
        }
        Servizio.ferma(c.applicationContext)
    }

    private fun esegui(
        ctx: Context, conversazione: List<ChatStorico.Messaggio>, domanda: String,
        chiave: String, chiaveGroq: String, mia: Int,
    ): String {
        val lc = Lingua.contesto(ctx)
        val storia = ChatStorico.contesto(conversazione)
        val a = Assistente(ctx)
        val g = Gemini(Gemini.Http(), chiave)
        gemini = g
        g.usaModelli(modelli(ctx))
        g.messaggi = messaggi(lc)
        val ascolto = object : Gemini.Ascolto {
            override fun testo(finora: String) = aggiorna(mia) { it.copy(parziale = finora) }
            override fun stato(fase: Int, dettaglio: String) = aggiorna(mia) {
                it.copy(
                    fase = when (fase) {
                        Gemini.INVIO -> lc.getString(R.string.ai_stato_invio, dettaglio)
                        Gemini.RIPROVO -> lc.getString(R.string.ai_stato_riprovo, dettaglio)
                        else -> lc.getString(R.string.ai_stato_dati)
                    },
                )
            }
        }
        g.ascolto = ascolto
        g.ricercaWeb = Assistente.vuoleRicerca(domanda)
        val istr = if (g.ricercaWeb) a.istruzioniProdotti() else a.istruzioni()
        var testo = ""
        try {
            testo = g.conversa(istr, storia, if (g.ricercaWeb) null else a.dichiarazioni(), a)
        } catch (eg: Gemini.Errore) {
            // Gemini finito o non disponibile: si passa a Groq (non per chiave errata o domanda fermata)
            if (eg.tipo == Gemini.Errore.ANNULLATA || eg.tipo == Gemini.Errore.CHIAVE || mia != generazione) throw eg
            ascolto.testo("")
            val quota = eg.tipo == Gemini.Errore.QUOTA
            if (quota) aggiorna(mia) { it.copy(fase = lc.getString(R.string.ai_stato_groq)) }
            val q = Groq(Groq.Http(), chiaveGroq)
            groq = q
            q.messaggi = g.messaggi
            q.erroreChiave = lc.getString(R.string.ai_err_chiave_groq)
            q.ascolto = if (quota) null else ascolto
            g.fonti.clear()
            try {
                testo = q.conversa(istr, ChatStorico.contesto(conversazione), a.dichiarazioni(), a,
                    if (g.ricercaWeb) Groq.RICERCA else Groq.STRUMENTI)
            } finally {
                UsoAi.aggiungi(ctx, 0, q.richieste)
            }
        } finally {
            UsoAi.aggiungi(ctx, g.richieste, 0)
        }
        if (g.fonti.isNotEmpty()) {
            val b = StringBuilder(testo).append("\n\n**").append(lc.getString(R.string.ai_fonti)).append("**")
            g.fonti.take(5).forEach { b.append("\n- ").append(it[0]) }
            testo = b.toString()
        }
        return testo
    }

    private fun notifica(ctx: Context, m: ChatStorico.Messaggio) {
        val c = Lingua.contesto(ctx)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CANALE_RISPOSTE, c.getString(R.string.notif_ai_canale), NotificationManager.IMPORTANCE_HIGH),
        )
        val testo = m.testo.replace("**", "").replace(Regex("(?m)^- "), "• ").trim()
        val apri = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_APRI_ASSISTENTE, true)
        }
        val pi = PendingIntent.getActivity(c, ID_RISPOSTA, apri, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CANALE_RISPOSTE)
            .setSmallIcon(R.drawable.ic_tab_chat)
            .setContentTitle(c.getString(if (m.ruolo == ChatStorico.ERRORE) R.string.notif_ai_errore else R.string.notif_ai_risposta))
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (testo.length > 600) testo.take(600) + "…" else testo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(ID_RISPOSTA, n) }
    }

    fun togliNotifica(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID_RISPOSTA)
    }
}

/** Servizio in primo piano attivo solo mentre l'assistente lavora: Android non interrompe la risposta ad app chiusa. */
class Servizio : Service() {
    companion object {
        private const val CANALE = "assistente_lavoro"
        private const val ID = 5251

        fun avvia(c: Context) {
            runCatching { c.startForegroundService(Intent(c, Servizio::class.java)) }
        }

        fun ferma(c: Context) {
            runCatching { c.stopService(Intent(c, Servizio::class.java)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = Lingua.contesto(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CANALE, c.getString(R.string.notif_ai_lavoro_canale), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val n: Notification = NotificationCompat.Builder(this, CANALE)
            .setSmallIcon(R.drawable.ic_tab_chat)
            .setContentTitle(c.getString(R.string.notif_ai_lavoro))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(ID, n)
            }
        } catch (e: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Motore.stato.value.inCorso && !Dieta.inCorso) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
