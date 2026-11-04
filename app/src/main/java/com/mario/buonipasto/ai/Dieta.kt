package com.mario.buonipasto.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
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
import java.io.ByteArrayOutputStream
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

    private fun istruzioni(giorni: Int): String {
        val lingua = if (Lingua.inglese()) "Scrivi i nomi degli alimenti in inglese." else "Scrivi i nomi degli alimenti in italiano."
        return """
            Sei un assistente che trasforma una dieta in una lista della spesa.
            Leggi tutta la dieta (tutti i giorni e tutti i pasti) e crea la lista per $giorni giorni:
            - se la dieta copre una settimana, ricalcola le quantità per $giorni giorni;
            - somma le quantità dello stesso alimento in pasti diversi;
            - trasforma le grammature in quantità da comprare (es. "petto di pollo 700 g", "uova 6", "yogurt greco 4 vasetti");
            - se un pasto propone alternative ("oppure", "a scelta"), usa la prima e ignora le altre;
            - includi anche olio, spezie e condimenti indicati, nel reparto dispensa;
            - non inventare alimenti che non sono nella dieta.
            $lingua
            Reparti ammessi: ${Reparti.CHIAVI.joinToString(", ")}.
            Rispondi SOLO con JSON valido, senza altro testo, in questo formato:
            {"articoli":[{"nome":"...","quantita":"...","reparto":"..."}]}
        """.trimIndent().let { PersoneDieta.aggiungi(it) }
    }

    private fun leggi(ctx: Context, uri: Uri, nomeFile: String, giorni: Int, chiave: String, chiaveGroq: String): List<Articolo> {
        val lc = Lingua.contesto(ctx)
        val byte = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException(lc.getString(R.string.dieta_non_apre))
        if (byte.size > MAX_BYTE) throw IllegalStateException(lc.getString(R.string.dieta_troppo_grande))
        val istr = istruzioni(giorni)
        val richiesta = lc.getString(R.string.dieta_richiesta, giorni)

        val g = Gemini(Gemini.Http(), chiave)
        g.usaModelli(Motore.modelli(ctx))
        g.messaggi = Motore.messaggi(lc)
        g.rispostaJson = true
        val testo = try {
            val storia = JsonArray().apply {
                add(Gemini.messaggioConFile(richiesta, "application/pdf", Base64.encodeToString(byte, Base64.NO_WRAP)))
            }
            g.conversa(istr, storia, null, null)
        } catch (eg: Gemini.Errore) {
            if (eg.tipo == Gemini.Errore.CHIAVE || eg.tipo == Gemini.Errore.ANNULLATA) throw eg
            // riserva: Groq non legge i PDF, quindi gli mando le prime pagine come immagini
            val q = Groq(Groq.Http(), chiaveGroq)
            q.messaggi = g.messaggi
            q.erroreChiave = lc.getString(R.string.ai_err_chiave_groq)
            try {
                q.conversa(istr, JsonArray().apply { add(messaggioConPagine(ctx, byte, richiesta)) }, null, null, Groq.FOTO)
            } finally {
                UsoAi.aggiungi(ctx, 0, q.richieste)
            }
        } finally {
            UsoAi.aggiungi(ctx, g.richieste, 0)
        }
        return analizza(testo, nomeFile)
    }

    /** Prime 3 pagine del PDF come JPEG (Groq accetta al massimo 3 immagini per richiesta). */
    private fun messaggioConPagine(ctx: Context, pdf: ByteArray, testo: String): JsonObject {
        val f = File(ctx.cacheDir, "dieta.pdf")
        f.writeBytes(pdf)
        val parts = JsonArray()
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                for (i in 0 until minOf(3, r.pageCount)) {
                    r.openPage(i).use { p ->
                        val larghezza = 1240
                        val altezza = (larghezza.toFloat() * p.height / p.width).toInt().coerceAtMost(2400)
                        val bmp = Bitmap.createBitmap(larghezza, altezza, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        bmp.recycle()
                        parts.add(JsonObject().apply {
                            add("inlineData", JsonObject().apply {
                                addProperty("mimeType", "image/jpeg")
                                addProperty("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                            })
                        })
                    }
                }
            }
        }
        f.delete()
        parts.add(JsonObject().apply { addProperty("text", testo) })
        return JsonObject().apply {
            addProperty("role", "user")
            add("parts", parts)
        }
    }

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
