import SwiftUI

/// Doorverbinden: kies een collega (met lampje) of tik een nummer in.
struct TransferView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @Environment(\.dismiss) private var dismiss
    @State private var number = ""
    @State private var failed = false

    var body: some View {
        NavigationStack {
            List {
                Section("Ander nummer") {
                    HStack {
                        TextField("Nummer", text: $number)
                            .keyboardType(.phonePad)
                        Button("Doorverbinden") { go(number) }
                            .disabled(number.isEmpty)
                    }
                }
                if let list = store.config?.colleagues, !list.isEmpty {
                    Section("Collega's") {
                        ForEach(list) { c in
                            Button {
                                go(c.ext)
                            } label: {
                                HStack(spacing: 12) {
                                    PresenceDot(state: phone.presence[c.ext] ?? .unknown)
                                    Text(c.name.isEmpty ? c.ext : c.name).foregroundStyle(.primary)
                                    Spacer()
                                    Text(c.ext).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                if failed {
                    Text("Doorverbinden is niet gelukt.").foregroundStyle(Theme.red)
                }
            }
            .navigationTitle("Doorverbinden")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleren") { dismiss() }
                }
            }
        }
    }

    private func go(_ n: String) {
        if phone.transfer(to: n) {
            dismiss()
        } else {
            failed = true
        }
    }
}
