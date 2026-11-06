import SwiftUI

/// Eerste scherm: QR-code scannen (of de link uit de camera-app openen).
struct SetupView: View {
    @EnvironmentObject var store: AccountStore
    @State private var scanning = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "phone.circle.fill")
                .font(.system(size: 88))
                .foregroundStyle(Theme.navy)
            Text("Verco Telefoon")
                .font(.largeTitle.bold())
            Text("Scan de QR-code die je van je beheerder krijgt.\nDaarna bel je met je eigen toestelnummer.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)

            if store.busy {
                ProgressView("Instellen…")
            } else {
                Button {
                    scanning = true
                } label: {
                    Label("QR-code scannen", systemImage: "qrcode.viewfinder")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 32)

                Button {
                    // instellink (verco-telefoon://setup?…) uit het klembord, bijv. uit een e-mail van de beheerder
                    if let text = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines),
                       let url = URL(string: text) {
                        Task { await store.setup(from: url) }
                    } else {
                        store.error = "Kopieer eerst de instellink (begint met verco-telefoon://) en tik dan opnieuw."
                    }
                } label: {
                    Label("Instellink plakken", systemImage: "doc.on.clipboard")
                }
                .font(.subheadline)
            }

            if let e = store.error {
                Text(e)
                    .foregroundStyle(Theme.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            Spacer()
            Text("Zorg dat je op de wifi van kantoor zit.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.bottom)
        }
        .sheet(isPresented: $scanning) {
            QRScannerView { code in
                scanning = false
                if let url = URL(string: code) {
                    Task { await store.setup(from: url) }
                }
            }
            .ignoresSafeArea()
        }
    }
}
