# Changelog

Versioni di BuoniPasto, dalla più recente.

## 3.9 — 2026-11-15: Debito di buoni ripagato in soldi

- Nel saldo dei buoni, "Debito ripagato in soldi": chi ha finito i buoni salda tutto il debito in contanti e il saldo dei buoni torna a zero.

## 3.8 — 2026-11-15: Bilancia animata e saldi validi per tutte le spese

- Nuova bilancia in stile iOS con molla e rimbalzo, selettore Buoni pasto / Soldi animato, riepilogo dei due saldi.
- Anche le spese inserite prima della 3.7 seguono la nuova logica dei due saldi.

## 3.7 — 2026-11-15: Saldi separati per buoni pasto e soldi

- Saldo dei buoni (in numero di buoni) e saldo dei soldi (in euro) separati: i buoni si pareggiano con le spese successive, i soldi con "Segna come saldato".

## 3.6 — 2026-11-05: Aggiungi spesa dalla lista

- Tasto "Aggiungi spesa" in cima alla lista, con i buoni suggeriti dalla stima.
- Prima di eliminare articoli spuntati senza una spesa registrata l'app chiede se si è registrato il pagamento.

## 3.5 — 2026-11-05: Stima della spesa tarata sulle spese reali

- Quando si registra una spesa, l'app la confronta con la stima degli articoli spuntati e corregge le stime successive (ultime 10 spese).

## 3.4 — 2026-11-05: Dieta e chiavi AI condivise nel gruppo

- Se uno dei due carica il PDF della dieta, menu e lista sono disponibili anche per l'altro.
- Le chiavi Gemini e Groq inserite da uno valgono per entrambi (salvate cifrate con il codice del gruppo).

## 3.3 — 2026-11-05: Spese inviate a BuyForTime

- Toccando una spesa nella Home: "Invia a BuyForTime" (buoni, valore del buono e parte in contanti) oppure "Condividi" come testo.

## 3.2 — 2026-11-04: Pulsanti della lista in alto

- "Elimina selezionati" e "Svuota la lista" sono in cima alla lista.

## 3.1 — 2026-11-04: PDF della dieta nelle Impostazioni

- Il PDF si carica una sola volta nelle Impostazioni: menu e alimenti per la spesa vengono salvati.
- "Crea la lista dalla dieta" chiede giorni e persone e crea la lista all'istante, senza rileggere il PDF.

## 3.0.3 — 2026-11-04: Menu tradotto nella lingua dell'app

- Se la dieta è in un'altra lingua il menu viene tradotto una volta e salvato.

## 3.0.2 — 2026-11-04: Tabella del menu sempre piena

- Le colonne delle persone si ricavano dagli alimenti letti ("Person A", "A (Mario)" e "A" sono la stessa persona).

## 3.0.1 — 2026-11-04: Corretto il crash all'avvio della 3.0

- Corretto l'arresto dell'app all'apertura introdotto con la scheda Menu.

## 3.0 — 2026-11-04: Nuova scheda Menu con i pasti del giorno

- Scheda "Menu" nella barra in basso: carica il PDF della dieta e mostra tutti i pasti del giorno, che cambiano da soli a mezzanotte.
- Settimana scorrevole, e per le diete di due persone una tabella con una colonna per persona (filtro Entrambi / A / B).

## 2.9 — 2026-11-04: Carni separate nella lista

- Pollo, vitello, manzo, maiale, pesce... restano prodotti diversi.

## 2.8 — 2026-11-04: Stima del costo della spesa

- Sotto la scheda della dieta: costo stimato degli articoli da prendere (prezzi medi da supermercato) e buoni pasto necessari.

## 2.7 — 2026-11-04: Yogurt greco, croissant e alimenti dei piatti composti

- Lo yogurt greco resta un prodotto a sé (non finisce più nei fiocchi di latte).
- "50 pz" di croissant e simili diventano grammi.
- Gli ingredienti dei piatti composti non vengono più saltati: se manca nel catalogo entrano comunque nella lista.

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
