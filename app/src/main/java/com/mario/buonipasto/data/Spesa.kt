package com.mario.buonipasto.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mario.buonipasto.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** Voce della lista della spesa. */
data class Articolo(
    val id: String = UUID.randomUUID().toString(),
    val nome: String,
    val quantita: String = "",
    val reparto: String = "altro",
    val preso: Boolean = false,
    /** Da dove arriva: nome del PDF della dieta, o vuoto se aggiunto a mano. */
    val fonte: String = "",
)

/** Reparti del supermercato, nell'ordine in cui si attraversano di solito. */
object Reparti {
    val CHIAVI = listOf(
        "frutta_verdura", "pane_cereali", "carne_pesce", "latticini_uova",
        "dispensa", "surgelati", "bevande", "altro",
    )

    fun nome(c: Context, chiave: String): String = c.getString(
        when (chiave) {
            "frutta_verdura" -> R.string.rep_frutta_verdura
            "pane_cereali" -> R.string.rep_pane_cereali
            "carne_pesce" -> R.string.rep_carne_pesce
            "latticini_uova" -> R.string.rep_latticini_uova
            "dispensa" -> R.string.rep_dispensa
            "surgelati" -> R.string.rep_surgelati
            "bevande" -> R.string.rep_bevande
            else -> R.string.rep_altro
        },
    )

    fun normalizza(chiave: String?): String = chiave?.trim()?.lowercase()?.takeIf { it in CHIAVI } ?: "altro"
}

/** Lista della spesa salvata sul telefono, osservabile dall'interfaccia. */
object ListaSpesa {
    private const val K = "lista"
    private val gson = Gson()
    private val _articoli = MutableStateFlow<List<Articolo>>(emptyList())
    val articoli: StateFlow<List<Articolo>> = _articoli
    private var caricata = false

    private fun prefs(c: Context) = c.getSharedPreferences("spesa", Context.MODE_PRIVATE)

    fun carica(c: Context) {
        if (caricata) return
        caricata = true
        val json = prefs(c).getString(K, null) ?: return
        _articoli.value = runCatching {
            gson.fromJson<List<Articolo>>(json, object : TypeToken<List<Articolo>>() {}.type).orEmpty()
                .filter { it.nome != null }
                .map { it.copy(reparto = Reparti.normalizza(it.reparto), quantita = it.quantita ?: "", fonte = it.fonte ?: "") }
        }.getOrDefault(emptyList())
    }

    private fun salva(c: Context, l: List<Articolo>) {
        _articoli.value = l
        prefs(c).edit().putString(K, gson.toJson(l)).apply()
    }

    fun aggiungi(c: Context, nuovi: List<Articolo>) {
        carica(c)
        val attuali = _articoli.value.toMutableList()
        for (n in nuovi) {
            // stesso alimento non ancora preso: unisco le quantita' invece di duplicarlo
            val i = attuali.indexOfFirst { !it.preso && it.nome.equals(n.nome, ignoreCase = true) }
            if (i >= 0) {
                val q = listOf(attuali[i].quantita, n.quantita).filter { it.isNotBlank() }.distinct().joinToString(" + ")
                attuali[i] = attuali[i].copy(quantita = q)
            } else {
                attuali.add(n)
            }
        }
        salva(c, attuali)
    }

    fun segna(c: Context, id: String, preso: Boolean) =
        salva(c, _articoli.value.map { if (it.id == id) it.copy(preso = preso) else it })

    fun elimina(c: Context, id: String) = salva(c, _articoli.value.filterNot { it.id == id })

    fun togliPresi(c: Context) = salva(c, _articoli.value.filterNot { it.preso })

    fun svuota(c: Context) = salva(c, emptyList())
}

/** Ultimi dati del gruppo, letti dall'assistente anche quando lavora in background. */
object DatiApp {
    @Volatile var session: Session? = null
    @Volatile var members: List<Member> = emptyList()
    @Volatile var movements: List<Movement> = emptyList()
}
