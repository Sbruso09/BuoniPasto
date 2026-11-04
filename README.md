# Buoni Pasto

App Android per due persone: ognuno entra con il proprio nome, aggiunge a mano le spese pagate con i buoni e vede chi deve quanto a chi. I dati si sincronizzano tra i telefoni con Firebase.

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

Per eseguire i test del saldo: `./gradlew test`.

## 3. Primo accesso

1. Sul primo telefono, scrivi il tuo nome, premi **Crea un nuovo codice**, indica il valore del tuo buono e premi **Entra**.
2. Sul secondo telefono, scrivi l'altro nome, lo stesso codice e il valore del buono.

## Come viene calcolato il saldo

- Spesa **a metà**: chi non ha pagato deve metà del totale.
- Spesa **solo mia**: nessun debito.
- Spesa **di [altra persona]**: l'altra persona deve tutto il totale.
- **Segna come saldato** registra il rimborso e riporta il saldo a zero.

Il totale di una spesa è: numero di buoni x valore del buono + eventuale importo in più. Il valore del buono viene salvato con ogni spesa, quindi cambiarlo non modifica quelle già inserite.

## Sicurezza

Non ci sono password: chi conosce il codice del gruppo e un nome può entrare con quel nome. Usa il codice generato dall'app e non condividerlo con altri. Il limite di due persone e il fatto che solo l'autore possa eliminare una voce sono controllati dall'app, non dalle regole di Firestore.
