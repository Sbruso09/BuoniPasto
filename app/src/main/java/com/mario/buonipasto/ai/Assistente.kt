package com.mario.buonipasto.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mario.buonipasto.Lingua
import com.mario.buonipasto.data.DatiApp
import com.mario.buonipasto.data.ListaSpesa
import com.mario.buonipasto.data.MovementType
import com.mario.buonipasto.data.Reparti
import com.mario.buonipasto.data.Split
import com.mario.buonipasto.data.netCents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cervello dell'assistente di Buoni Pasto: istruzioni, strumenti in sola lettura sui dati
 * del gruppo (saldo, spese, movimenti, lista della spesa) e modalita' "consigli prodotti"
 * con ricerca web.
 */
class Assistente(private val ctx: Context) : Gemini.Esecutore {

    private fun euro(cents: Long): Double = Math.round(cents.toDouble()) / 100.0

    private fun data(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun oggi(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN))

    private fun lingua(): String =
        if (Lingua.inglese()) "Rispondi SEMPRE in inglese (English), anche se i dati sono in italiano"
        else "Rispondi sempre in italiano"

    // ------------------------------------------------------------------ istruzioni

    fun istruzioni(): String = """
        Sei l'assistente di Buoni Pasto, un'app usata da due persone che pagano la spesa con i buoni pasto e tengono il conto di chi deve quanto a chi.
        Oggi è ${oggi()}. ${lingua()}, in modo amichevole, chiaro e breve.

        REGOLE:
        1. Per numeri su saldo, spese e buoni usa i DATI ATTUALI qui sotto o gli strumenti. Non inventare cifre.
        2. Sei in sola lettura: non puoi aggiungere, modificare o eliminare spese. Se te lo chiedono, spiega che si fa dall'app.
        3. Se ti servono più strumenti chiamali tutti insieme nello stesso turno.
        4. Quando parli di soldi indica anche quanti buoni corrispondono (importo / valore del buono).
        5. Formattazione: solo **grassetto** ed elenchi con '- '. Niente tabelle.

        DATI ATTUALI:
        ${fotografia()}
    """.trimIndent()

    fun istruzioniProdotti(): String = """
        Sei l'assistente di Buoni Pasto, un'app usata da due persone che fanno la spesa con i buoni pasto.
        Oggi è ${oggi()}. ${lingua()}, in modo chiaro e breve.

        COMPITO: l'utente chiede consigli su cosa comprare. Usa la ricerca web per trovare prezzi attuali nei supermercati italiani
        (Esselunga, Coop, Conad, Carrefour, Lidl, Eurospin, Pam...) e confronta i prodotti per rapporto qualità/prezzo:
        - consiglia 2-4 prodotti concreti (marca e formato) dicendo perché convengono;
        - confronta il prezzo al kg o al litro, non solo il prezzo della confezione;
        - indica alternative economiche (marca del supermercato, discount) e quando vale la pena spendere di più;
        - converti la spesa in buoni usando il valore del buono dei DATI (buoni = prezzo / valore del buono);
        - se la lista della spesa contiene quel prodotto, tienine conto.
        Non inventare prezzi: se non li trovi dillo e dai una stima indicando che è una stima.
        Formattazione: solo **grassetto** ed elenchi con '- '.

        DATI ATTUALI:
        ${fotografia()}
    """.trimIndent()

    private fun fotografia(): String = runCatching { statoBuoni().toString() }.getOrDefault("{}")

    // ------------------------------------------------------------------ strumenti

    private fun prop(tipo: String, descr: String) = JsonObject().apply {
        addProperty("type", tipo)
        addProperty("description", descr)
    }

    private fun funzione(nome: String, descr: String, proprieta: JsonObject? = null) = JsonObject().apply {
        addProperty("name", nome)
        addProperty("description", descr)
        add("parameters", JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", proprieta ?: JsonObject())
        })
    }

    fun dichiarazioni(): JsonArray = JsonArray().apply {
        add(funzione("stato_buoni", "Saldo attuale tra le due persone, valore dei buoni, totali del mese corrente e del precedente."))
        add(funzione("spese_periodo", "Totali di un periodo per persona: speso, buoni usati, quote a metà.", JsonObject().apply {
            add("da", prop("STRING", "Data iniziale AAAA-MM-GG"))
            add("a", prop("STRING", "Data finale AAAA-MM-GG (inclusa)"))
        }))
        add(funzione("elenco_movimenti", "Ultime spese e rimborsi, con filtri facoltativi.", JsonObject().apply {
            add("limite", prop("INTEGER", "Quanti movimenti (massimo 60, predefinito 20)"))
            add("persona", prop("STRING", "Nome di chi ha pagato (facoltativo)"))
            add("testo", prop("STRING", "Parola da cercare nelle note (facoltativo)"))
        }))
        add(funzione("lista_spesa", "Articoli nella lista della spesa, per reparto, con quelli già presi."))
    }

    override fun esegui(nome: String, a: JsonObject): JsonObject = when (nome) {
        "stato_buoni" -> statoBuoni()
        "spese_periodo" -> spesePeriodo(dataArg(a, "da"), dataArg(a, "a"))
        "elenco_movimenti" -> elenco(
            if (a.has("limite")) a.get("limite").asInt else 20,
            if (a.has("persona")) a.get("persona").asString else null,
            if (a.has("testo")) a.get("testo").asString else null,
        )
        "lista_spesa" -> listaSpesa()
        else -> JsonObject().apply { addProperty("errore", "Strumento sconosciuto: $nome") }
    }

    private fun dataArg(a: JsonObject, k: String): LocalDate? =
        runCatching { LocalDate.parse(a.get(k).asString.trim().take(10)) }.getOrNull()

    private fun statoBuoni(): JsonObject {
        val o = JsonObject()
        val s = DatiApp.session
        val membri = DatiApp.members
        val mov = DatiApp.movements
        o.addProperty("oggi", LocalDate.now().toString())
        if (s == null) {
            o.addProperty("nota", "Nessun gruppo: l'utente non ha ancora fatto l'accesso.")
            return o
        }
        val me = membri.firstOrNull { it.id == s.memberId }
        val altro = membri.firstOrNull { it.id != s.memberId }
        o.addProperty("io", s.name)
        o.addProperty("altra_persona", altro?.name ?: "(non ancora entrata nel gruppo)")
        me?.let { o.addProperty("valore_mio_buono", euro(it.voucherCents)) }
        altro?.let { o.addProperty("valore_buono_altra_persona", euro(it.voucherCents)) }
        if (altro != null) {
            val net = netCents(mov, s.memberId)
            o.addProperty("saldo", when {
                net > 0 -> "${altro.name} deve ${euro(net)} € a ${s.name}"
                net < 0 -> "${s.name} deve ${euro(-net)} € a ${altro.name}"
                else -> "pari"
            })
        }
        val ora = YearMonth.now()
        o.add("mese_corrente", totali(mov.filter { YearMonth.from(data(it.dateMillis)) == ora }))
        o.add("mese_precedente", totali(mov.filter { YearMonth.from(data(it.dateMillis)) == ora.minusMonths(1) }))
        o.addProperty("movimenti_totali", mov.size)
        val lista = ListaSpesa.articoli.value
        if (lista.isNotEmpty()) {
            o.addProperty("lista_spesa", lista.filter { !it.preso }.joinToString(", ") {
                it.nome + if (it.quantita.isNotBlank()) " (${it.quantita})" else ""
            }.take(1500))
        }
        return o
    }

    private fun totali(l: List<com.mario.buonipasto.data.Movement>): JsonObject {
        val o = JsonObject()
        val spese = l.filter { it.type == MovementType.EXPENSE }
        o.addProperty("totale_spese", euro(spese.sumOf { it.totalCents }))
        o.addProperty("numero_spese", spese.size)
        val per = JsonObject()
        for (m in DatiApp.members) {
            val mie = spese.filter { it.payerId == m.id }
            per.add(m.name, JsonObject().apply {
                addProperty("pagato", euro(mie.sumOf { it.totalCents }))
                addProperty("buoni_usati", mie.sumOf { it.vouchers })
                addProperty("extra_carta_contanti", euro(mie.sumOf { it.extraCents }))
                addProperty("spese_a_meta", mie.count { it.split == Split.HALF })
            })
        }
        o.add("per_persona", per)
        return o
    }

    private fun spesePeriodo(da: LocalDate?, a: LocalDate?): JsonObject {
        val inizio = da ?: LocalDate.now().withDayOfMonth(1)
        val fine = a ?: LocalDate.now()
        val l = DatiApp.movements.filter { val d = data(it.dateMillis); !d.isBefore(inizio) && !d.isAfter(fine) }
        return totali(l).apply {
            addProperty("da", inizio.toString())
            addProperty("a", fine.toString())
            addProperty("rimborsi", l.count { it.type == MovementType.SETTLEMENT })
        }
    }

    private fun elenco(limite: Int, persona: String?, testo: String?): JsonObject {
        val n = limite.coerceIn(1, 60)
        val l = DatiApp.movements
            .filter { persona.isNullOrBlank() || it.payerName.equals(persona.trim(), ignoreCase = true) }
            .filter { testo.isNullOrBlank() || it.note.contains(testo.trim(), ignoreCase = true) }
            .take(n)
        val arr = JsonArray()
        for (m in l) arr.add(JsonObject().apply {
            addProperty("data", data(m.dateMillis).toString())
            addProperty("tipo", if (m.type == MovementType.SETTLEMENT) "rimborso" else "spesa")
            addProperty("pagato_da", m.payerName)
            if (m.type == MovementType.SETTLEMENT) addProperty("ricevuto_da", m.receiverName)
            addProperty("totale", euro(m.totalCents))
            addProperty("buoni", m.vouchers)
            addProperty("extra", euro(m.extraCents))
            addProperty("divisione", when (m.split) {
                Split.HALF -> "a metà"
                Split.PAYER_ONLY -> "solo di chi ha pagato"
                Split.OTHER_ONLY -> "solo dell'altra persona"
            })
            if (m.note.isNotBlank()) addProperty("nota", m.note)
        })
        return JsonObject().apply { add("movimenti", arr) }
    }

    private fun listaSpesa(): JsonObject {
        val l = ListaSpesa.articoli.value
        val o = JsonObject()
        for (rep in Reparti.CHIAVI) {
            val voci = l.filter { it.reparto == rep }
            if (voci.isEmpty()) continue
            val arr = JsonArray()
            for (v in voci) arr.add(JsonObject().apply {
                addProperty("nome", v.nome)
                if (v.quantita.isNotBlank()) addProperty("quantita", v.quantita)
                addProperty("preso", v.preso)
            })
            o.add(rep, arr)
        }
        if (l.isEmpty()) o.addProperty("nota", "La lista della spesa è vuota.")
        return o
    }

    companion object {
        /** true se la domanda chiede consigli su prodotti e prezzi: si usa la ricerca web. */
        fun vuoleRicerca(domanda: String): Boolean {
            val d = " " + domanda.lowercase(Locale.ROOT) + " "
            val chiavi = listOf(
                "consigli", "prodott", "marca", "marche", "conviene comprare", "meglio comprare", "cosa compro",
                "prezzo", "prezzi", "offert", "supermercat", "discount", "qualità/prezzo", "qualita prezzo",
                "recommend", "product", "brand", "price", "cheaper", "best buy", "which to buy", "deal",
            )
            return chiavi.any { d.contains(it) }
        }

        /** Separa la riga finale "PRODOTTO: ..." se presente (non usata qui, ma tenuta per coerenza). */
        fun pulisci(testo: String): String = testo.trim()
    }
}
