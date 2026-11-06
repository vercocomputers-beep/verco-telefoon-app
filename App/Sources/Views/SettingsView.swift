import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @State private var confirmLogout = false

    var body: some View {
        NavigationStack {
            List {
                if let cfg = store.config {
                    Section("Account") {
                        row("Naam", cfg.user.name)
                        row("Toestelnummer", cfg.user.ext)
                        row("Bedrijf", cfg.company)
                        row("Status", phone.status.text)
                    }
                    Section("Centrale") {
                        row("Adres", cfg.sip.port == 5060 ? cfg.sip.domain : "\(cfg.sip.domain):\(cfg.sip.port)")
                        row("Gebruiker", cfg.sip.username)
                    }
                }
                Section {
                    Button("Instellingen verversen bij de centrale") {
                        Task { await store.refresh(showErrors: true) }
                    }
                    if let e = store.error {
                        Text(e).font(.caption).foregroundStyle(Theme.red)
                    }
                    Button("Opnieuw verbinden") {
                        if let cfg = store.config {
                            phone.stop()
                            phone.start(with: cfg)
                        }
                    }
                    Button("Afmelden van deze telefoon", role: .destructive) { confirmLogout = true }
                }
                Section {
                    row("Versie", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                } footer: {
                    Text("Verco Telefoon is open source (GPLv3) en gebruikt de Linphone-bouwdoos van Belledonne Communications.")
                }
            }
            .navigationTitle("Instellingen")
            .confirmationDialog("Afmelden? Je moet daarna opnieuw de QR-code scannen.", isPresented: $confirmLogout,
                                titleVisibility: .visible) {
                Button("Afmelden", role: .destructive) { store.logout() }
            }
        }
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack {
            Text(k)
            Spacer()
            Text(v).foregroundStyle(.secondary).lineLimit(1)
        }
    }
}
