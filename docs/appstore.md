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

> Verco Telefoon is a business softphone for companies that use a Verco Voip Centrale (Asterisk-based PBX).
> Accounts are provisioned by the company's administrator with a QR code. For review, open the demo setup link below
> on the device (or copy it and tap "Instellink plakken" in the app). The demo extension can call 500 (echo test).
>
> Demo setup link: `verco-telefoon://setup?s=…&u=…&p=…`  ← vul in: demo-gebruiker op de centrale van Verco (bereikbaar via internet)

Voor de review is dus een **demo-gebruiker** nodig op een centrale die vanaf internet bereikbaar is
(bijv. `verco.sip.cloud2go.nl`, met *Apps ook buiten het kantoornetwerk* aan).
