# Changelog

Versioni di BuoniPasto, dalla più recente.

## 2.6 — 2026-11-04: Spesa per giorni della settimana e quantità modificabili

- Al posto di 3/7/14 giorni si scelgono i giorni della settimana (anche non consecutivi): la lista contiene solo gli alimenti di quei giorni.
- Tenendo premuto un articolo si modificano nome e quantità, con i tasti − e +, oppure lo si elimina.

## 2.5 — 2026-11-04: Correzioni su uova e caffè

- Le uova indicate in grammi diventano pezzi (50 g = 1 uovo, albumi esclusi).
- Il caffè indicato come tazzina non viene più contato come 130 g: 7 g a tazzina.

## 2.4 — 2026-11-04: Nomi degli alimenti coerenti tra i giorni

- Primo passaggio con il catalogo degli alimenti della dieta; i giorni usano solo quei nomi (niente doppioni).
- Temperatura 0: lo stesso PDF dà sempre lo stesso risultato.

## 2.3 — 2026-11-04: Lettura della dieta giorno per giorno

- La dieta viene letta un giorno alla volta (quattro richieste in parallelo), così nessun giorno viene saltato.

## 2.2 — 2026-11-04: Quantità della lista calcolate dall'app

- L'AI estrae dal PDF ogni alimento con quantità e unità; somme e proporzioni le calcola l'app, non il modello.
- Riserva Groq: le pagine del PDF vengono lette come immagini, tre alla volta.

## 2.1 — 2026-11-04: Dieta per 1 o 2 persone e unione di due diete

- Quando si importa una dieta l'app chiede se la spesa è per 1 o 2 persone; con 2 le quantità raddoppiano.
- Nuovo suggerimento nella chat: "Unisci due diete" sceglie due PDF e crea una dieta unica compatibile (Gemini, con Groq come riserva).

## 2.0 — 2026-11-04: Stile iOS, lingua, impostazioni e assistente AI

- Grafica in stile iOS, lingua italiano/inglese, pagina Impostazioni.
- Assistente AI (Gemini con Groq come riserva): resoconti, consigli sui prodotti con ricerca web.
- Lista della spesa dal PDF della dieta.

## 1.0 — 2026-11-04: Prima versione

- Gruppo di due persone con codice, spese pagate con i buoni pasto e saldo tra le due persone.
