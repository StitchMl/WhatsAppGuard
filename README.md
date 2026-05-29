# WhatsApp Guard

App Android di prova che usa un `AccessibilityService` per coprire e bloccare alcuni controlli di WhatsApp:

- schermata chat: barra inferiore, pulsante fotocamera, menu a tre pallini, pulsante Meta AI quando individuabile;
- dentro una chat: menu a tre pallini e microfono per messaggi vocali.

I rettangoli sono `TYPE_ACCESSIBILITY_OVERLAY`: stanno sopra WhatsApp e consumano i tocchi, quindi i pulsanti sotto non ricevono il click.

## Uso

1. Apri il progetto in Android Studio.
2. Esegui l'app sul telefono.
3. Premi `Apri impostazioni accessibilita`.
4. Abilita `WhatsApp Guard`.
5. Apri WhatsApp e verifica le aree bloccate.

Per ridurre disattivazioni accidentali puoi anche premere `Attiva amministratore dispositivo`.
Questo mostra un avviso prima di disattivare l'amministratore, ma non puo impedire al proprietario
del telefono di revocarlo dalle impostazioni.

## Blocco forte con device owner

Il blocco forte deve usare il canale ufficiale Android `device owner`/MDM. Dopo aver installato
l'app su un telefono predisposto, il componente admin e:

```text
com.codex.whatsappguard/.AdminReceiver
```

Quando l'app e device owner, il pulsante `Applica protezioni device owner`:

- blocca la disinstallazione dell'app;
- disabilita la schermata di controllo app dove possibile;
- impedisce safe boot;
- limita i servizi di accessibilita non di sistema alla sola app.

Su molti telefoni `adb shell dpm set-device-owner ...` funziona solo se il dispositivo non ha gia
account configurati e non ha un altro device owner. In caso contrario Android richiede reset o
provisioning MDM/Android Enterprise.

## Limite importante

Android non consente a una normale app di rendersi non disattivabile o di proteggere la rimozione del controllo amministratore con una password scelta dall'app. Il proprietario del telefono deve poter revocare accessibilita, amministratore dispositivo e app dalle impostazioni.

Per un dispositivo gestito in modo ufficiale servono Android Enterprise `device owner`, MDM o parental control. Questi scenari richiedono consenso e configurazione del dispositivo, spesso prima del primo uso o dopo reset di fabbrica.

## Taratura

WhatsApp cambia spesso etichette e layout. Se un pulsante non viene coperto, modifica le liste in:

`app/src/main/kotlin/com/codex/whatsappguard/GuardAccessibilityService.kt`

In particolare:

- `cameraLabels`
- `moreLabels`
- `micLabels`
- `bottomNavLabels`
- `findMetaAiNode`

Il fallback di Meta AI usa una posizione approssimata in basso a destra nella schermata principale.
