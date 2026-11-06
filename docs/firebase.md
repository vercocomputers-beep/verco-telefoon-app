# Android-push instellen (Firebase), eenmalig

Hiermee gaat de Android-app over, ook als Android de app heeft gesloten. Kosten: geen.
Eén Firebase-project voor alle klant-centrales.

## 1. Firebase-project maken

1. Ga naar <https://console.firebase.google.com> en log in met het Google-account van Verco.
2. **Project maken** → naam `Verco Telefoon` → Google Analytics **uit** → *Project maken*.

## 2. Android-app toevoegen → google-services.json

1. In het project: het **Android-icoon** (*App toevoegen*).
2. Pakketnaam: `org.verco.telefoon` (precies zo). Bijnaam: `Verco Telefoon`. SHA-1 leeg laten.
3. *App registreren* → **google-services.json downloaden**.
4. De overige stappen (SDK toevoegen) overslaan met *Volgende* / *Doorgaan naar console*.

## 3. Serviceaccount-sleutel → .json

1. Tandwiel linksboven → **Projectinstellingen** → tabblad **Serviceaccounts**.
2. **Nieuwe privésleutel genereren** → *Sleutel genereren*. Er wordt een `.json`-bestand gedownload.
3. Dit bestand is geheim: niet mailen, niet in de app-repository zetten.

Controle: tabblad **Cloud Messaging** → *Firebase Cloud Messaging API (V1)* staat op **Ingeschakeld**.

## 4. In de centrale

Instellingen → **Mobiel** → *Eigen app: overgaan als de app dicht is*:

| Veld | Wat |
|---|---|
| Android-push: google-services.json | inhoud van `google-services.json` (openen in Kladblok, alles kopiëren) |
| Android-push: serviceaccount-sleutel | inhoud van het serviceaccount-`.json` |

Opslaan en **Toepassen**. Bij een fout zegt de centrale welk bestand niet klopt.

## 5. In de app

App openen (of Instellingen → *Instellingen verversen*). De app meldt zich daarna aan met een pushsleutel.
Testen: app wegvegen uit de recente apps, telefoon op slot, het toestelnummer bellen.

## Hoe het werkt

- De app krijgt de Firebase-gegevens (niet geheim) van de centrale via `/prov/vt` → `push.fcm`.
  Daarom staat er niets van Firebase in de openbare broncode.
- De app meldt zich aan met `pn-provider=fcm;pn-prid=<sleutel>;pn-param=<project>` (RFC 8599).
- Bij een gesprek stuurt de centrale eerst een datapush (hoge prioriteit) via de FCM HTTP v1 API, ondertekend met de
  serviceaccount-sleutel. De app wordt wakker, meldt zich opnieuw aan en dan gaat hij over.
