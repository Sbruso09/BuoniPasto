package com.mario.buonipasto.ai

import android.content.Context
import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mario.buonipasto.Lingua
import com.mario.buonipasto.R
import com.mario.buonipasto.data.Articolo
import com.mario.buonipasto.data.ListaSpesa
import com.mario.buonipasto.data.Reparti
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors

/** Esito dell'ultima lettura di una dieta, per la schermata della lista. */
data class StatoDieta(val inCorso: Boolean = false, val messaggio: String? = null, val errore: Boolean = false)

/**
 * Legge il PDF di una dieta e crea la lista della spesa.
 * Gemini legge direttamente il PDF; se ha finito le richieste gratuite, le prime pagine
 * vengono trasformate in immagini e lette da Groq.
 */
object Dieta {
    private const val MAX_BYTE = 15 * 1024 * 1024
    private val esecutore = Executors.newSingleThreadExecutor()
    private val _stato = MutableStateFlow(StatoDieta())
    val stato: StateFlow<StatoDieta> = _stato
    @Volatile var inCorso = false
        private set

    fun chiudiMessaggio() {
        if (!_stato.value.inCorso) _stato.value = StatoDieta()
    }

    /** Prima chiede per quante persone e' la spesa, poi legge la dieta. */
    fun importa(contesto: Context, uri: Uri, nomeFile: String, giorni: Int) {
        PersoneDieta.chiedi(contesto, uri, nomeFile, giorni)
    }

    fun importaDavvero(contesto: Context, uri: Uri, nomeFile: String, giorni: Int) {
        if (inCorso) return
        val ctx = contesto.applicationContext
        val lc = Lingua.contesto(ctx)
        val chiave = ChiaveAi.leggi(ctx)
        val chiaveGroq = ChiaveAi.leggi(ctx, ChiaveAi.GROQ)
        if (chiave == null || chiaveGroq == null) {
            _stato.value = StatoDieta(false, lc.getString(R.string.dieta_servono_chiavi), true)
            return
        }
        inCorso = true
        _stato.value = StatoDieta(true, lc.getString(R.string.dieta_in_lettura, nomeFile))
        Servizio.avvia(ctx)
        esecutore.execute {
            val esito = try {
                val articoli = leggi(ctx, uri, nomeFile, giorni, chiave, chiaveGroq)
                if (articoli.isEmpty()) {
                    StatoDieta(false, lc.getString(R.string.dieta_niente), true)
                } else {
                    ListaSpesa.aggiungi(ctx, articoli)
                    StatoDieta(false, lc.resources.getQuantityString(R.plurals.dieta_aggiunti, articoli.size, articoli.size, nomeFile))
                }
            } catch (e: Gemini.Errore) {
                StatoDieta(false, e.message, true)
            } catch (e: Throwable) {
                StatoDieta(false, lc.getString(R.string.dieta_errore, e.message ?: e.toString()), true)
            }
            inCorso = false
            _stato.value = esito
            if (!Motore.stato.value.inCorso) Servizio.ferma(ctx)
        }
    }

    /**
     * Lettura in piu' passi (catalogo degli alimenti, poi un giorno alla volta) e somma fatta dall'app:
     * vedi [DietaLettore] e [EstrattoreDieta].
     */
    private fun leggi(ctx: Context, uri: Uri, nomeFile: String, giorni: Int, chiave: String, chiaveGroq: String): List<Articolo> =
        DietaLettore.leggi(ctx, uri, nomeFile, giorni, chiave, chiaveGroq)

    /** Estrae gli articoli dal JSON della risposta (anche se il modello aggiunge testo intorno). */
    fun analizza(testo: String, fonte: String): List<Articolo> {
        val inizio = testo.indexOf('{')
        val fine = testo.lastIndexOf('}')
        if (inizio < 0 || fine <= inizio) return emptyList()
        val json = runCatching { JsonParser.parseString(testo.substring(inizio, fine + 1)).asJsonObject }.getOrNull()
            ?: return emptyList()
        val arr = json.getAsJsonArray("articoli") ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val nome = o.get("nome")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            if (nome.isEmpty()) return@mapNotNull null
            Articolo(
                nome = nome.replaceFirstChar { it.uppercase() },
                quantita = o.get("quantita")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty(),
                reparto = Reparti.normalizza(o.get("reparto")?.takeIf { !it.isJsonNull }?.asString),
                fonte = fonte,
            )
        }
    }
}
