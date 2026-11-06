import SwiftUI

/// Functies van de centrale: voicemail, niet storen, wachtrij, gesprek overnemen.
struct FunctionsView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager

    var body: some View {
        NavigationStack {
            List {
                if let vm = store.config?.voicemail, !vm.isEmpty {
                    Section {
                        Button {
                            phone.call(vm)
                        } label: {
                            Label("Voicemail beluisteren", systemImage: "recordingtape")
                        }
                    }
                }
                Section("Functies") {
                    ForEach(store.config?.functions ?? []) { f in
                        Button {
                            phone.call(f.code)
                        } label: {
                            HStack {
                                Label(f.name, systemImage: icon(for: f.code))
                                Spacer()
                                Text(f.code).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Functies")
        }
    }

    private func icon(for code: String) -> String {
        switch code {
        case "*97": return "recordingtape"
        case "*78": return "moon.fill"
        case "*79": return "moon"
        case "*89": return "arrow.down.left.circle"
        case "*62", "*63": return "pause.circle"
        default: return "star"
        }
    }
}
