import SwiftUI

/// Collega's (met lampje) en contacten uit het telefoonboek van de centrale.
struct ContactsView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @ObservedObject var phonebook: Phonebook
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List {
                if !colleagues.isEmpty {
                    Section("Collega's") {
                        ForEach(colleagues) { c in
                            Button {
                                phone.call(c.ext)
                            } label: {
                                HStack(spacing: 12) {
                                    PresenceDot(state: phone.presence[c.ext] ?? .unknown)
                                    VStack(alignment: .leading) {
                                        Text(c.name.isEmpty ? c.ext : c.name).foregroundStyle(.primary)
                                        Text("\(c.ext) · \(label(phone.presence[c.ext] ?? .unknown))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "phone").foregroundStyle(Theme.green)
                                }
                            }
                        }
                    }
                }
                if !contacts.isEmpty {
                    Section("Contacten") {
                        ForEach(contacts) { entry in
                            Button {
                                if let n = entry.mainNumber { phone.call(n) }
                            } label: {
                                HStack {
                                    Image(systemName: "person.crop.circle.fill")
                                        .font(.title2)
                                        .foregroundStyle(Theme.accent)
                                    VStack(alignment: .leading) {
                                        Text(entry.name).foregroundStyle(.primary)
                                        Text(entry.numbers.map { $0.number }.joined(separator: " · "))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "phone").foregroundStyle(Theme.green)
                                }
                            }
                        }
                    }
                }
            }
            .overlay {
                if colleagues.isEmpty && contacts.isEmpty {
                    Text(phonebook.error ?? "Nog geen contacten").foregroundStyle(.secondary)
                }
            }
            .searchable(text: $search, prompt: "Zoek naam of nummer")
            .refreshable {
                if let url = store.config?.phonebook { await phonebook.load(from: url) }
            }
            .navigationTitle("Contacten")
        }
    }

    private var query: String { search.trimmingCharacters(in: .whitespaces).lowercased() }

    private var colleagues: [ProvisioningConfig.Colleague] {
        let all = store.config?.colleagues ?? []
        guard !query.isEmpty else { return all }
        return all.filter { $0.name.lowercased().contains(query) || $0.ext.contains(query) }
    }

    /// Telefoonboek zonder de collega's (die staan hierboven met lampje).
    private var contacts: [PhonebookEntry] {
        let exts = Set((store.config?.colleagues ?? []).map { $0.ext })
        let list = phonebook.entries.filter { e in !e.numbers.contains { exts.contains($0.number) } }
        guard !query.isEmpty else { return list }
        return list.filter { e in e.name.lowercased().contains(query) || e.numbers.contains { $0.number.contains(query) } }
    }

    private func label(_ p: PhoneManager.Presence) -> String {
        switch p {
        case .available: return "vrij"
        case .busy: return "in gesprek"
        case .dnd: return "niet storen"
        case .offline: return "niet bereikbaar"
        case .unknown: return "onbekend"
        }
    }
}

struct PresenceDot: View {
    let state: PhoneManager.Presence

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 14, height: 14)
            .overlay(Circle().stroke(Color.primary.opacity(0.15), lineWidth: 1))
    }

    private var color: Color {
        switch state {
        case .available: return Theme.green
        case .busy, .dnd: return Theme.red
        case .offline: return .gray.opacity(0.5)
        case .unknown: return .gray.opacity(0.25)
        }
    }
}
