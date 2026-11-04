package com.mario.buonipasto.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DietaTest {
    @Test
    fun leggeIlJsonDellaLista() {
        val testo = """Ecco la lista:
            {"articoli":[
              {"nome":"petto di pollo","quantita":"700 g","reparto":"carne_pesce"},
              {"nome":"Zucchine","quantita":"1 kg","reparto":"FRUTTA_VERDURA"},
              {"nome":"olio extravergine","quantita":"","reparto":"condimenti"},
              {"nome":"","quantita":"2","reparto":"altro"}
            ]}
            Buona spesa!"""
        val a = Dieta.analizza(testo, "dieta marzo")
        assertEquals(3, a.size)
        assertEquals("Petto di pollo", a[0].nome)
        assertEquals("carne_pesce", a[0].reparto)
        assertEquals("frutta_verdura", a[1].reparto)
        assertEquals("altro", a[2].reparto) // reparto sconosciuto
        assertEquals("dieta marzo", a[0].fonte)
    }

    @Test
    fun testoSenzaJson() {
        assertTrue(Dieta.analizza("non riesco a leggere il file", "x").isEmpty())
        assertTrue(Dieta.analizza("{rotto", "x").isEmpty())
    }

    @Test
    fun riconosceLeDomandeSuiProdotti() {
        assertTrue(Assistente.vuoleRicerca("Quale marca di pasta conviene comprare?"))
        assertTrue(Assistente.vuoleRicerca("Which brand is the best buy for olive oil?"))
        assertFalse(Assistente.vuoleRicerca("Fammi un riepilogo delle spese di questo mese."))
        assertFalse(Assistente.vuoleRicerca("Chi ha pagato di più?"))
    }
}
