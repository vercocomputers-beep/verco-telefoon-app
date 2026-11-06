import SwiftUI

/// Recente gesprekken: inkomend, uitgaand en gemist. Tikken = terugbellen.
struct RecentsView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @ObservedObject var recents = RecentStore.shared
    @State private var onlyMissed = false
    @State private var confirmClear = false

    var body: some View {
        NavigationStack {
            List(list) { c in
                Button {
                    phone.call(c.number)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: icon(c.kind))
                            .foregroundStyle(c.kind == .missed ? Theme.red : .secondary)
                            .frame(width: 22)
                        VStack(alignment: .leading) {
                            Text(title(c))
                                .foregroundStyle(c.kind == .missed ? Theme.red : .primary)
                            Text(subtitle(c))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(c.date, format: .dateTime.day().month().hour().minute())
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .overlay {
                if list.isEmpty {
                    Text(onlyMissed ? "Geen gemiste gesprekken" : "Nog geen gesprekken").foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Recent")
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Picker("", selection: $onlyMissed) {
                        Text("Alle").tag(false)
                        Text("Gemist").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 180)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Wissen") { confirmClear = true }
                        .disabled(recents.calls.isEmpty)
                }
            }
            .confirmationDialog("Alle recente gesprekken wissen?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Wissen", role: .destructive) { recents.clear() }
            }
            .onAppear { recents.unseenMissed = 0 }
        }
    }

    private var list: [RecentCall] {
        onlyMissed ? recents.calls.filter { $0.kind == .missed } : recents.calls
    }

    /// Naam uit de lijst met collega's als die bekend is.
    private func title(_ c: RecentCall) -> String {
        if let col = store.config?.colleagues?.first(where: { $0.ext == c.number }), !col.name.isEmpty { return col.name }
        return c.name.isEmpty ? c.number : c.name
    }

    private func subtitle(_ c: RecentCall) -> String {
        let what: String
        switch c.kind {
        case .incoming: what = "inkomend"
        case .outgoing: what = "uitgaand"
        case .missed: what = "gemist"
        }
        let dur = c.duration > 0 ? String(format: " · %d:%02d", c.duration / 60, c.duration % 60) : ""
        return "\(c.number) · \(what)\(dur)"
    }

    private func icon(_ k: RecentCall.Kind) -> String {
        switch k {
        case .incoming: return "phone.arrow.down.left"
        case .outgoing: return "phone.arrow.up.right"
        case .missed: return "phone.down"
        }
    }
}
