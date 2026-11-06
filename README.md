# Verco Telefoon

App voor de **Verco Voip Centrale** (Asterisk-gebaseerde telefooncentrale per klant).
Opvolger van Groundwire als mobiele SIP-app: aanmelden bij de centrale van de klant,
bellen/gebeld worden binnen én buiten kantoor (via de DNS-naam `<klantcode>.sip.cloud2go.nl`),
met push voor inkomende oproepen.

## Status

**Versie 0.2 (fase 2 van het plan).** Bouwt in GitHub Actions; nog niet getest op een iPhone (wacht op het Apple-account).

| Onderdeel | Stand |
|---|---|
| Instellen met QR-code (`verco-telefoon://setup?s=…&u=…&p=…`), camera of scanner in de app | gebouwd |
| Instellingen ophalen bij de centrale (`/prov/vt`, JSON versie 1) en veilig bewaren (sleutelhanger) | gebouwd |
| Aanmelden en bellen via liblinphone (Linphone-bouwdoos) | gebouwd |
| Kiesscherm, gespreksscherm (stil, luidspreker, toetsen), contacten, functies (★), instellingen | gebouwd |
| Gesprekken als normaal iPhone-gesprek (CallKit: vergrendelscherm, Recent, knoppen van iOS) | gebouwd |
| Overgaan als de app dicht is (PushKit + push vanuit de centrale) | volgt (Apple-account nodig) |
| Collega's met lampje (presence/BLF) | volgt |
| App-icoon (Verco-logo) en accentkleur | gebouwd |
| Android-versie | later |

## Hoe het gebouwd wordt

- Het Xcode-project wordt **niet** opgeslagen maar gemaakt met [XcodeGen](https://github.com/yonaskolb/XcodeGen) uit `project.yml`.
- Bij elke push naar `main` bouwt **GitHub Actions** de app op een Mac in de cloud (simulator, zonder ondertekening) als controle:
  zie het tabblad *Actions* op GitHub. Fouten staan daar als rode meldingen.
- Testversies op de iPhone komen later via **TestFlight**, zodra het Apple Developer-account van Verco VOF actief is.

Op een Mac zelf bouwen:

```
brew install xcodegen
bash scripts/linphone-sdk.sh
xcodegen generate
open VercoTelefoon.xcodeproj
```

## Mappen

| Map | Inhoud |
|---|---|
| `App/Sources/App` | start van de app, kleuren |
| `App/Sources/Model` | instellingen (QR-link, ophalen, sleutelhanger), telefoonboek |
| `App/Sources/Phone` | bellen via liblinphone (`PhoneManager`) en CallKit (`CallKitManager`) |
| `App/Resources` | app-icoon en kleuren (Assets.xcassets) |
| `App/Sources/Views` | schermen |
| `.github/workflows` | bouwcontrole in GitHub Actions |
| `scripts/linphone-sdk.sh` | haalt de Linphone-bouwdoos op (vaste versie) naar `Packages/linphone-sdk` |

## Afspraken met de centrale

- QR-code bij Gebruiker → App (instelling *Eigen app Verco Telefoon (test)* aan): `verco-telefoon://setup?s=<adres centrale>&u=<app-gebruiker>&p=<app-wachtwoord>`
- `GET <adres centrale>/prov/vt?u=…&p=…` geeft JSON: `version`, `company`, `user{name, extension}`, `sip{username, password, domain, port, transport}`, `voicemail`, `video`, `codecs`, `phonebook` (vCard-link), `functions[{name, code}]`, `push{enabled}`.

## Licentie

GNU General Public License v3.0, zie `LICENSE`. Gebruikt [liblinphone](https://www.linphone.org) (AGPLv3/GPLv3) van Belledonne Communications.

## Naar GitHub

Dubbelklik **`Naar GitHub.cmd`** in deze map om wijzigingen naar GitHub te sturen
(vraagt om beheerdersrechten; eerste keer: `gh auth login`). De map wordt ook
meegenomen door **Verco-NaarGitHub-Alles.cmd** in de bovenliggende map.
