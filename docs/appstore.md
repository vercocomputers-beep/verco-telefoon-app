# App Store en TestFlight: teksten en stappen

## Eenmalig in Apple (zodra het account actief is)

1. **App-ID registreren** — developer.apple.com → Certificates, Identifiers & Profiles → Identifiers → **+** → App IDs → App.
   - Description: `Verco Telefoon`, Bundle ID (Explicit): `org.verco.telefoon`
   - Capabilities: **Push Notifications** aanvinken. Opslaan.
2. **Pushsleutel (voor de centrale)** — Keys → **+** → naam `Verco push`, vink **Apple Push Notifications service (APNs)** aan → Continue → Register → **Download** het `.p8`-bestand (kan maar één keer!). Noteer de **Key ID**.
   In de centrale: Geavanceerde opties → Mobiel → *Apple-push*: Team ID, Key ID, inhoud van de .p8.
3. **App aanmaken in App Store Connect** — appstoreconnect.apple.com → Apps → **+** → Nieuwe app: platform iOS, naam `Verco Telefoon`, taal Nederlands, bundel-ID `org.verco.telefoon`, SKU `verco-telefoon`.
4. **API-sleutel voor GitHub** — App Store Connect → Gebruikers en toegang → Integraties → App Store Connect API → **+** → naam `GitHub`, toegang **App Manager** → Download `AuthKey_XXXX.p8` (één keer!). Noteer **Key ID** en **Issuer ID**.
5. **Geheimen bij GitHub** — github.com/vercocomputers-beep/verco-telefoon-app → Settings → Secrets and variables → Actions → New repository secret:
   - `APPLE_TEAM_ID` = Team ID (Apple Developer → Membership)
   - `ASC_KEY_ID` = Key ID uit stap 4
   - `ASC_ISSUER_ID` = Issuer ID uit stap 4
   - `ASC_KEY_P8` = hele inhoud van het AuthKey-bestand uit stap 4
6. **Bouwen** — GitHub → Actions → **Naar TestFlight** → Run workflow. Na ± 15–30 minuten staat de versie in App Store Connect → TestFlight.
7. **Testen** — installeer de app **TestFlight** uit de App Store op de iPhone; voeg jezelf toe als interne tester (App Store Connect → TestFlight → Interne testers).

## Privacyverklaring

Pagina: `docs/privacy/index.html`. Zichtbaar maken via GitHub Pages: repository → Settings → Pages → Source: *Deploy from a branch*, branch `main`, map `/docs` → Save.
Adres wordt: `https://vercocomputers-beep.github.io/verco-telefoon-app/privacy/`

## Teksten voor de App Store

- **Naam:** Verco Telefoon
- **Ondertitel:** Bel met je eigen toestelnummer
- **Categorie:** Zakelijk
- **Beschrijving:**

  Verco Telefoon maakt van je iPhone een toestel van de telefooncentrale van je bedrijf (Verco Voip Centrale).
  Je belt en wordt gebeld met je eigen toestelnummer, binnen en buiten kantoor.

  • Instellen in één keer: scan de QR-code van je beheerder, klaar.
  • Gesprekken verschijnen als gewoon iPhone-gesprek, ook op het vergrendelscherm.
  • Collega's met lampje: zie wie vrij is of in gesprek.
  • Contacten van de centrale, recente gesprekken en voicemail met teller.
  • Doorverbinden, niet storen, gesprek overnemen en wachtrij aan/af.

  Voor gebruik is een account op een Verco Voip Centrale nodig (via je werkgever).

- **Trefwoorden:** telefooncentrale,voip,sip,zakelijk,toestel,softphone,bellen,voicemail,verco
- **Ondersteuning-URL:** https://github.com/vercocomputers-beep/verco-telefoon-app (of de website van Verco)
- **Privacybeleid-URL:** https://vercocomputers-beep.github.io/verco-telefoon-app/privacy/
- **Leeftijd:** 4+
- **Gegevensverzameling (App Privacy):** "Geen gegevens verzameld" (de app stuurt niets naar Verco; gesprekken lopen via de centrale van de werkgever).

## Notitie voor Apple (App Review)

Plakken bij App Store Connect → de app → *App Review Information* → **Notes** (en vink *Sign-in required* uit:
inloggen gaat met een link, niet met gebruikersnaam en wachtwoord).

> Verco Telefoon is a business softphone for companies that use a Verco Voip Centrale (an Asterisk-based phone system).
> Employees do not create accounts: the company's administrator gives each employee a QR code, which sets up the app
> automatically.
>
> **How to test:**
> 1. Copy this setup link, open the app and tap "Instellink plakken" (Paste setup link), or open the link on the device:
>    `verco-telefoon://setup?s=…&u=…&p=…`
> 2. The app connects to our demo phone system (status "Aangemeld" = registered).
> 3. Outgoing call: dial **\*43** (echo test: you hear yourself).
> 4. Incoming call: dial **\*44** and hang up. The phone system calls you back after about 10 seconds. This also works
>    when the app is closed or the device is locked (VoIP push → CallKit).
> 5. "Collega's" shows colleagues with a presence light, "Contacten" the company phone book.
>
> The app only connects to the company phone system that issued the setup link. It does not collect data and has no
> in-app purchases. The source code is public (GPLv3): https://github.com/vercocomputers-beep/verco-telefoon-app

## Demo-gebruiker klaarzetten (eenmalig, op de centrale van Verco)

De centrale moet voor Apple vanaf internet bereikbaar zijn. Vanaf centrale **3.02** gaat dat via een aparte
**app-poort**: daarop staan alleen de instellingen en het telefoonboek voor de app, nooit de beheerpagina.

1. **Certificaat** (de iPhone accepteert geen zelfgemaakt certificaat): Beveiliging → Certificaat →
   *Domeinnaam* = `verco.sip.cloud2go.nl`, Cloudflare API-token (sjabloon *DNS-zone bewerken*, alleen zone
   `cloud2go.nl`), e-mailadres → *Certificaat aanvragen*.
2. **Mobiel:** *Eigen app Verco Telefoon* aan, *Apps ook buiten het kantoornetwerk* aan, *Poort voor de eigen app* = 8444.
3. **Router (UniFi), doorsturen naar de centrale (192.168.80.56):** TCP **8444** (nieuw), UDP 5060 en UDP 10000–10200 (staan er al).
4. **Gebruiker** aanmaken: naam `Apple Review`, toestelnummer bijv. `199`, app aan, geen voicemail-e-mail. Opslaan, **Toepassen**.
5. Gebruiker → **App** → de link onder *Verco Telefoon* kopiëren en hierboven in de notitie plakken.
6. Zelf controleren via 4G (wifi uit): link openen, *43 bellen, *44 bellen en ophangen → de app gaat over.

Na de keuring de gebruiker *Apple Review* laten staan: Apple test ook bij elke update.
