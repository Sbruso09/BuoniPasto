# Buoni Pasto

App Android per due persone che fanno la spesa con i buoni pasto: spese condivise, due saldi separati
(buoni e soldi), lista della spesa e menu del giorno creati dal PDF della dieta, assistente AI.
Le novità di ogni versione sono nel [CHANGELOG](CHANGELOG.md).

## Funzioni principali

- **Spese e saldi:** ogni spesa registra buoni usati, valore del buono e parte pagata in più. I buoni finiscono nel
  saldo dei buoni (in numero di buoni, si pareggia con le spese successive o ripagando in soldi), i soldi nel saldo
  dei soldi (si chiude con "Segna come saldato"). Bilancia animata in stile iOS.
- **Dieta:** il PDF si carica una volta nelle Impostazioni (condiviso con l'altra persona del gruppo). La scheda
  **Menu** mostra i pasti del giorno; la **Lista** si crea scegliendo giorni della settimana e persone, con stima del
  costo tarata sulle spese reali.
- **Assistente AI:** Gemini con Groq di riserva (chiavi gratuite, condivise nel gruppo).
- **BuyForTime:** ogni spesa si può inviare all'app [BuyForTime](https://github.com/sbruso09/BuyForTime).

L'APK viene compilato automaticamente da GitHub Actions a ogni modifica e pubblicato nella sezione **Releases** del repository.

I dati si sincronizzano tra i telefoni con Firebase.

## 1. Crea il progetto Firebase (piano gratuito Spark)

1. Vai su https://console.firebase.google.com e crea un progetto.
2. Aggiungi un'app Android con nome pacchetto `com.mario.buonipasto`.
3. Scarica `google-services.json` e copialo nella cartella `app/`. Senza questo file la build non parte.
4. In **Authentication > Metodo di accesso**, attiva **Anonimo**.
5. In **Firestore Database**, crea il database in modalità produzione, con una regione europea.
6. Nella scheda **Regole** di Firestore, incolla il contenuto di `firestore.rules` e pubblica.

## 2. Compila e installa

1. Apri la cartella `BuoniPasto` in Android Studio e attendi la sincronizzazione di Gradle. Serve JDK 17 o superiore: va bene quello incluso in Android Studio.
2. Collega il telefono con il debug USB attivo e premi **Run**.
3. Per il secondo telefono, usa **Build > Build APK(s)** e invia il file `app/build/outputs/apk/debug/app-debug.apk`.

### Firma

La chiave di firma **non** è nel repository. Per firmare con la propria chiave (così ogni aggiornamento si installa
sopra la versione già presente), metti il file in `app/buonipasto.keystore` e la password in `~/.gradle/gradle.properties`
come `buonipastoPassword=...` (oppure nella variabile d'ambiente `BUONIPASTO_KEY_PASSWORD`).
Su GitHub Actions si usano i secret `BUONIPASTO_KEYSTORE_BASE64` (la keystore in base64) e `BUONIPASTO_KEY_PASSWORD`.
Senza chiave l'APK viene firmato con la chiave di debug.

Per eseguire i test del saldo: `./gradlew test`.

## 3. Primo accesso

1. Sul primo telefono, scrivi il tuo nome, premi **Crea un nuovo codice**, indica il valore del tuo buono e premi **Entra**.
2. Sul secondo telefono, scrivi l'altro nome, lo stesso codice e il valore del buono.

## Come vengono calcolati i saldi

- Spesa **a metà**: chi non ha pagato deve metà dei buoni e metà della parte in soldi.
- Spesa **solo mia**: nessun debito.
- Spesa **di [altra persona]**: l'altra persona deve tutti i buoni e tutta la parte in soldi.
- **Saldo buoni:** contato in numero di buoni; si pareggia quando chi è in debito paga le spese successive con i buoni,
  oppure con **Debito ripagato in soldi**.
- **Saldo soldi:** spese senza buoni e parti pagate in contanti o carta; si chiude con **Segna come saldato**.

Il valore del buono viene salvato con ogni spesa, quindi cambiarlo non modifica quelle già inserite.

## Sicurezza

Non ci sono password: chi conosce il codice del gruppo e un nome può entrare con quel nome. Usa il codice generato dall'app e non condividerlo con altri. Il limite di due persone e il fatto che solo l'autore possa eliminare una voce sono controllati dall'app, non dalle regole di Firestore.

## Licenza

Copyright © 2026 Mario Borruso. **Tutti i diritti riservati.** Il codice è pubblico solo per essere consultato:
copia, modifica, distribuzione e qualsiasi uso, anche commerciale, richiedono un'autorizzazione scritta.
Vedi [LICENSE](LICENSE).
