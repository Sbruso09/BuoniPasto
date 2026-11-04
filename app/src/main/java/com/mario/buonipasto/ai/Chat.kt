package com.mario.buonipasto.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken

/** Cronologia della chat con l'assistente, salvata solo sul telefono. */
object ChatStorico {
    const val UTENTE = "utente"
    const val AI = "ai"
    const val ERRORE = "errore"
    private const val K = "chat"
    private const val MAX = 300
    private const val CONTESTO = 20
    private val gson = Gson()

    data class Messaggio(val ruolo: String, val testo: String, val ts: Long = System.currentTimeMillis())

    private fun prefs(c: Context) = c.getSharedPreferences("ai", Context.MODE_PRIVATE)

    fun leggi(c: Context): List<Messaggio> {
        val json = prefs(c).getString(K, null) ?: return emptyList()
        return runCatching {
            val tipo = object : TypeToken<List<Messaggio>>() {}.type
            gson.fromJson<List<Messaggio>>(json, tipo).orEmpty().filter { it.testo != null && it.ruolo != null }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun aggiungi(c: Context, m: Messaggio) {
        val l = leggi(c).toMutableList()
        l.add(m)
        while (l.size > MAX) l.removeAt(0)
        prefs(c).edit().putString(K, gson.toJson(l)).apply()
    }

    fun cancella(c: Context) {
        prefs(c).edit().remove(K).apply()
    }

    /** Conversazione per Gemini: ultimi messaggi utente/AI, gli errori restano fuori. */
    fun contesto(messaggi: List<Messaggio>): JsonArray {
        val validi = messaggi.filter { it.ruolo != ERRORE }
        var da = maxOf(0, validi.size - CONTESTO)
        while (da < validi.size && validi[da].ruolo != UTENTE) da++
        val a = JsonArray()
        for (i in da until validi.size) {
            val m = validi[i]
            a.add(Gemini.messaggio(if (m.ruolo == UTENTE) "user" else "model", m.testo))
        }
        return a
    }
}
