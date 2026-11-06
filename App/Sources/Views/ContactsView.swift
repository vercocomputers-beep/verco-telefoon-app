import SwiftUI

/// Collega's en contacten uit het telefoonboek van de centrale.
struct ContactsView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @ObservedObject var phonebook: Phonebook
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List(filtered) { entry in
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
            .overlay {
                if phonebook.entries.isEmpty {
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

    private var filtered: [PhonebookEntry] {
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return phonebook.entries }
        return phonebook.entries.filter { e in
            e.name.lowercased().contains(q) || e.numbers.contains { $0.number.contains(q) }
        }
    }
}
