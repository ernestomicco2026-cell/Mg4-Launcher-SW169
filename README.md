# MG4 Launcher — SW169 Color Radio

🇮🇹 [Italiano](#italiano) · 🇬🇧 [English](#english)

**Ultima versione / Latest version: [0.1.0](releases/MG4Launcher-SW169-0.1.0.apk)**

> ⚠️ **Compatibilità / Compatibility:** solo infotainment **SW169 Color Radio** · only **SW169 Color Radio** infotainment.
> Per **SW168 SAIC 1.0+** usa [mg4-launcher](https://github.com/ernestomicco2026-cell/mg4-launcher) · for **SW168 SAIC 1.0+** use [mg4-launcher](https://github.com/ernestomicco2026-cell/mg4-launcher).

> Progetto amatoriale e **non ufficiale**: non è affiliato né approvato da MG Motor o SAIC Motor.
> I nomi MG / MG4 servono solo a indicare l'auto compatibile (infotainment SW169). Installalo e usalo a tuo rischio.
>
> Hobby project, **not official**: not affiliated with or endorsed by MG Motor or SAIC Motor.
> The MG / MG4 names only identify the compatible car (SW169 infotainment). Install and use it at your own risk.

---

## Italiano

Launcher in stile Tesla per l'infotainment della **MG4** con software **SW169 Color Radio**
(EH32, Android Automotive 9, build `SWI69-29958-1100R45`).

**Compatibilità:** funziona **solo con il software SW169 Color Radio**. La SW169 Color Radio non è un aggiornamento
della SW168: è un software diverso, con altri servizi dell'auto. Su SW168 usa la [versione SW168](https://github.com/ernestomicco2026-cell/mg4-launcher).
Sostituisce la schermata home originale mantenendone le funzioni, con la stessa grafica della versione SW168.

### Cosa fa

- **Auto e batteria** (a sinistra): autonomia, percentuale, barra batteria con tacca del limite di ricarica,
  stato di ricarica e V2L, temperatura esterna. La tua MG4 ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
  Tocca l'auto o V2L → app Energia. Tieni premuta l'auto → impostazioni del launcher.
- **Mappa / Media / CarPlay** (a destra):
  - **Mappa**: indicazioni di guida da CarPlay / Android Auto. Senza guida: Navigatore e la riga **Apri con**
    (CarPlay/Android Auto se il telefono è collegato, Waze/Google Maps se installati sull'auto; MG solo se l'auto ha
    un navigatore MG). Tieni premuto un pulsante della riga per renderlo il navigatore predefinito.
  - **Media**: parte dalla sorgente in riproduzione, altrimenti dalla radio; in alto un pulsante per ogni sorgente
    (Radio, USB, Bluetooth, Online se disponibile, e CarPlay/Android Auto se il telefono è collegato).
    Per la radio: nome e logo della stazione, pulsanti **FM / DAB** per cambiare banda e **preferiti** con i loghi.
    **Tieni premuto il riquadro della stazione** per aggiungerla o toglierla dai preferiti; tocca un preferito per
    sintonizzarlo, tienilo premuto per eliminarlo.
  - **CarPlay / Android Auto**: la scheda compare da sola quando colleghi il telefono.
- **Barra in basso**: temperatura ‹ › (LO, 16…30, HI), ventola ‹ › (OFF / 1…11 / A = automatico;
  tocca l'icona = app Clima, tieni premuto = accendi/spegni), mini-player, Telefono, Radio, Telecamere, Impostazioni.
- **App**: scorri la home verso sinistra per le app dell'auto e quelle che installi tu,
  con le icone tutte nello stesso stile; scorri verso destra per tornare. Con **Modifica** scegli quali app installate mostrare.
- **Impostazioni** (tieni premuta l'auto): colore dell'auto, tetto nero, tema (automatico, notte, giorno),
  navigatore predefinito (Automatico, MG, Telefono), aggiornamenti, diagnostica con **Salva log**.
  Si aprono anche dall'ingranaggio in alto a destra nella pagina App.
- **Schermo intero**: barra di stato e dock MG nascosti (trascina dal bordo per richiamarli).
- **Aggiornamenti**: le nuove versioni si scaricano e si installano dalle impostazioni, senza computer.

### Installazione

Solo per infotainment **SW169 Color Radio**. Serve un computer con [adb](https://developer.android.com/tools/releases/platform-tools) collegato all'auto.
Il firmware MG blocca l'installazione normale delle app: l'opzione `-f` la permette.

1. Scarica l'APK dalla cartella [`releases/`](releases/).
2. Installa:
   ```bash
   adb install -r -f MG4Launcher-SW169-0.1.0.apk
   ```
3. Rendilo la home predefinita:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   In alternativa premi il tasto Home: Android chiede quale home usare.

Il launcher parte già all'accensione dell'auto, come la home originale.

### Aggiornamenti

Impostazioni del launcher → **Aggiornamenti** → **Controlla aggiornamenti**.
Se c'è una versione più nuova compare **Scarica e installa**: il launcher la installa da solo e si riavvia.
Gli aggiornamenti arrivano solo da questo repository: una versione SW168 non viene mai proposta.
Si possono installare anche da computer, con lo stesso comando dell'installazione.

### Tornare alla home MG originale

Dal launcher: impostazioni → **Apri la home originale MG**. Per renderla di nuovo quella predefinita:

```bash
adb shell cmd package set-home-activity com.saicmotor.launcher/.view.MainActivity
```

Per rimuovere del tutto il launcher: `adb uninstall com.mg4.launcher`. L'app originale non viene mai modificata.

### Comandi utili

```bash
# riavviare il launcher senza spegnere l'auto
adb shell am force-stop com.mg4.launcher
adb shell am start -n com.mg4.launcher/.MainActivity

# vedere i valori letti dall'auto (Ctrl+C per fermare)
adb logcat -s MG4L MG4L.Link

# scaricare la diagnostica salvata dal launcher (aggiornata ogni 15 s)
adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt
```

### Segnalare un problema

Nelle impostazioni la colonna **Diagnostica servizi auto** mostra i valori grezzi letti da ogni servizio
(veicolo, batteria, clima, media, radio, CarPlay, navigatore) e gli errori recenti. Uno screenshot di quella colonna
o il file `diagnostica.txt` bastano per capire cosa succede.

Con **Salva log** (in alto nella colonna Diagnostica) il launcher scrive un log completo — valori, errori, eventi
recenti e logcat del launcher — in `Android/data/com.mg4.launcher/files/mg4-launcher-log-….txt`.
Se è inserita una chiavetta USB il file finisce anche lì, nella stessa cartella.

**Prima prova (0.1.0):** questa versione è stata costruita dal firmware, senza poterla provare sull'auto.
In Diagnostica controlla soprattutto `veicolo.stato`, `bms.stato`, `clima.*`, `media.usb`, `media.bt`,
`media.radio.sessione` e `radio.corrente`, poi manda il log salvato.

### Versioni

#### 0.1.0 — 12 ottobre 2026
- Prima versione per **SW169 Color Radio**, con la grafica e le correzioni della versione SW168 0.4.1.
- Servizi dell'auto riscritti per la SW169: batteria, ricarica, V2L e clima dal servizio `caradapter`;
  limite di ricarica, potenza e consumo dal CarService (come l'app Energia).
- Clima **16–30 °C** (LO / HI agli estremi), come sulla MG4 EU con SW169.
- Media con le sessioni standard di Android (USB, Bluetooth, online, radio); bande DAB / FM.
- Preferiti radio gestiti dal launcher (l'app Radio SW169 non li rende leggibili alle altre app).
- V2L e auto aprono l'app Energia; pulsante Telecamere come la home originale SW169.
- Pulsante "MG" del navigatore solo se l'auto ha un navigatore MG.
- Aggiornamenti solo da questo repository.

### Compilare

Nessun Gradle: `./build.sh` usa aapt2, javac (bytecode 8), D8 e firma con la chiave platform AOSP
(la stessa del sistema dell'auto, build test-keys: identica su SW168 e SW169). L'APK esce in `build/`.
La versione è in `version.properties`.

Prima di compilare metti in `tools/` i due file non inclusi nel repository:
- `android.jar` (API 28) da [Sable/android-platforms](https://github.com/Sable/android-platforms);
- `d8.jar`: è `lib/jadx-1.5.1-all.jar` di [jadx 1.5.1](https://github.com/skylot/jadx/releases/tag/v1.5.1), rinominato.

`keys/platform.p12` è la chiave platform **pubblica** di test AOSP (password `android`).

**Pubblicare una nuova versione:** aumenta `VERSION_CODE` e `VERSION_NAME` in `version.properties`, compila,
copia l'APK in `releases/MG4Launcher-SW169-X.Y.Z.apk`, aggiorna `releases/latest.json` (versione, nome del file
e dimensione) e le Versioni qui sopra, poi commit e push. Il launcher controlla `releases/latest.json` e anche le
Release di GitHub con un APK allegato, e propone la più recente.
Per la Release di GitHub basta crearla con il tag `vX.Y.Z`: l'automazione `.github/workflows/allega-apk.yml`
allega da sola `releases/MG4Launcher-SW169-X.Y.Z.apk` (e, se la descrizione è vuota, usa le note di `latest.json`).

### Come comunica con l'auto

Servizi del software **SW169 Color Radio** (ricavati dall'analisi del firmware `SWI69-29958-1100R45`).

| Funzione | Servizio | Note |
|---|---|---|
| Batteria, autonomia, ricarica, V2L | `com.saicmotor.caradapter` → `queryClient(4)` → `ICarEvService` | come la home originale (carapi) |
| Clima, ventola, temperatura esterna | stesso hub → `queryClient(7)` → `ICarHvacService` | 16–30 °C a mezzi gradi, ventola 1–11 / auto |
| Limite di ricarica, potenza, consumo | CarService Android, manager `bms` | limite: codice 1–6 = 40–90%, altrimenti 100% |
| USB / Online | MediaBrowserService di `com.saicmotor.media` | |
| Bluetooth | `A2dpMediaBrowserService` di `com.android.bluetooth` | |
| Radio | sessione `RadioMBService` + `IRadioManager` di `com.saicmotor.radio` | DAB / FM, logo DAB |
| Telecamere | `com.saicmotor.evs` (EvsService) | |
| CarPlay / Android Auto | `com.allgo.rui` + MediaBrowser `com.allgo.remoteui.mediabrowserservice` | metadati e indicazioni |
| Navigatore MG (se presente) | `com.saicmotor.adapterservice` (GeneralService + notifiche) | |

La mappa del navigatore non può essere mostrata dentro il launcher (è un'altra app):
il pannello Mappa mostra le indicazioni e apre il navigatore a schermo intero.

### Note

- L'immagine dell'auto deriva da una foto ufficiale MG; marchi e immagini appartengono ai rispettivi proprietari.
- I loghi delle stazioni FM vengono da [radio-browser.info](https://www.radio-browser.info), database pubblico e gratuito.

---

## English

Tesla-style launcher for the **MG4** infotainment system running **SW169 Color Radio** software
(EH32, Android Automotive 9, build `SWI69-29958-1100R45`).

**Compatibility:** works **only with SW169 Color Radio**. SW169 Color Radio is not an update of SW168: it is different
software with different car services. On SW168 use the [SW168 version](https://github.com/ernestomicco2026-cell/mg4-launcher).
It replaces the original home screen, keeping its features, with the same design as the SW168 version.

### Features

- **Car and battery** (left): range, percentage, battery bar with a charge-limit mark, charging and V2L status,
  outside temperature. Your MG4, recolourable with the 8 factory colours, optional black roof.
  Tap the car or V2L → Energy app. Long-press the car → launcher settings.
- **Map / Media / CarPlay** (right):
  - **Map**: guidance from CarPlay / Android Auto. When not navigating: Navigator and the **Open with** row
    (CarPlay/Android Auto when a phone is connected, Waze/Google Maps if installed; MG only if the car has an MG navigator).
    Long-press a button in that row to make it the default navigator.
  - **Media**: starts on the source that is playing, otherwise the radio; one button per source at the top
    (Radio, USB, Bluetooth, Online when available, plus CarPlay/Android Auto when a phone is connected).
    Radio: station name and logo, **FM / DAB** buttons and **favourites** with logos.
    **Long-press the station tile** to add or remove it; tap a favourite to tune in, long-press it to delete it.
  - **CarPlay / Android Auto**: the tab appears by itself when you connect a phone.
- **Bottom bar**: temperature ‹ › (LO, 16…30, HI), fan ‹ › (OFF / 1…11 / A = auto;
  tap the icon = Climate app, long-press = climate on/off), mini player, Phone, Radio, Cameras, Settings.
- **Apps**: swipe the home screen left for the car's apps and the ones you install; swipe right to go back.
  Use **Modifica** (Edit) to choose which installed apps to show.
- **Settings** (long-press the car): car colour, black roof, theme, default navigator, updates,
  diagnostics with **Salva log** (save log).
- **Full screen**: status bar and MG dock hidden (swipe from the edge to bring them back).
- **Updates**: new versions download and install from the settings, no computer needed.

The app's interface is in Italian.

### Installation

Only for **SW169 Color Radio** infotainment. You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools) connected to the car.

1. Download the APK from the [`releases/`](releases/) folder.
2. Install it:
   ```bash
   adb install -r -f MG4Launcher-SW169-0.1.0.apk
   ```
3. Make it the default home:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```

### Updates

Launcher settings → **Aggiornamenti** (Updates) → **Controlla aggiornamenti** (Check for updates).
Updates come only from this repository: a SW168 build is never offered.

### Going back to the original MG home

From the launcher: settings → **Apri la home originale MG**. To make it the default again:

```bash
adb shell cmd package set-home-activity com.saicmotor.launcher/.view.MainActivity
```

To remove the launcher completely: `adb uninstall com.mg4.launcher`. The original app is never modified.

### Reporting a problem

Settings → **Diagnostica servizi auto** shows the raw values read from each car service and recent errors;
**Salva log** writes a full log to `Android/data/com.mg4.launcher/files/`. Version 0.1.0 was built from the firmware
without testing on a car, so the saved log is the quickest way to fix anything that does not work.

### Changelog

#### 0.1.0 — 12 October 2026
- First version for **SW169 Color Radio**, with the design and fixes of SW168 version 0.4.1.
- Car services rewritten for SW169 (`caradapter` hub, CarService `bms` properties, standard Android media sessions).
- Climate range **16–30 °C**; radio favourites kept by the launcher; V2L and the car open the Energy app;
  Cameras button like the original SW169 home; MG navigator button only when present; updates only from this repository.
