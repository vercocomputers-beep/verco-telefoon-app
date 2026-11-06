import SwiftUI

/// Verco Telefoon: app voor de Verco Voip Centrale.
/// Instellen gaat met de QR-code uit de centrale (Gebruiker → App), daarna bel je met je eigen toestelnummer.
@main
struct VercoTelefoonApp: App {
    @StateObject private var store = AccountStore.shared
    @StateObject private var phone = PhoneManager.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(phone)
                .tint(Theme.accent)
                .onOpenURL { url in
                    Task { await store.setup(from: url) }
                }
                .task {
                    if let cfg = store.config { phone.start(with: cfg) }
                }
                .onChange(of: store.config) { cfg in
                    if let cfg { phone.start(with: cfg) } else { phone.stop() }
                }
        }
    }
}

enum Theme {
    static let navy = Color(red: 0x12 / 255, green: 0x35 / 255, blue: 0x5b / 255)
    static let accent = Color(red: 0x1b / 255, green: 0x4f / 255, blue: 0x8a / 255)
    static let green = Color(red: 0x1f / 255, green: 0x88 / 255, blue: 0x3d / 255)
    static let red = Color(red: 0xcf / 255, green: 0x22 / 255, blue: 0x2e / 255)
}
