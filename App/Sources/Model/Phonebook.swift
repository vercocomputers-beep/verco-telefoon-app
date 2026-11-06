import Foundation

struct PhonebookEntry: Identifiable, Hashable {
    let id: String
    let name: String
    let numbers: [(number: String, kind: String)]

    static func == (a: PhonebookEntry, b: PhonebookEntry) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }

    var mainNumber: String? { numbers.first?.number }
}

/// Telefoonboek van de centrale: collega's, contacten en functies (vCard van de CardDAV-link).
@MainActor
final class Phonebook: ObservableObject {
    @Published private(set) var entries: [PhonebookEntry] = []
    @Published var error: String?

    func load(from urlString: String) async {
        guard let url = URL(string: urlString) else { return }
        do {
            let (data, resp) = try await URLSession.shared.data(from: url)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else {
                error = "Telefoonboek niet beschikbaar."
                return
            }
            entries = Phonebook.parse(String(decoding: data, as: UTF8.self))
                .filter { !$0.name.hasPrefix("★") }
            error = nil
        } catch {
            self.error = "Telefoonboek niet bereikbaar."
        }
    }

    static func parse(_ text: String) -> [PhonebookEntry] {
        var out: [PhonebookEntry] = []
        var uid = "", name = ""
        var nums: [(String, String)] = []
        // vCard-regels die verder gaan op de volgende regel (beginnen met spatie) samenvoegen
        let lines = text.replacingOccurrences(of: "\r\n ", with: "").replacingOccurrences(of: "\n ", with: "")
            .components(separatedBy: .newlines)
        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line == "BEGIN:VCARD" {
                uid = ""; name = ""; nums = []
            } else if line.hasPrefix("UID:") {
                uid = String(line.dropFirst(4))
            } else if line.hasPrefix("FN:") {
                name = unescape(String(line.dropFirst(3)))
            } else if line.hasPrefix("TEL") , let idx = line.firstIndex(of: ":") {
                let head = line[..<idx].uppercased()
                let kind = head.contains("CELL") ? "mobiel" : (head.contains("WORK") ? "werk" : "")
                nums.append((String(line[line.index(after: idx)...]), kind))
            } else if line == "END:VCARD", !name.isEmpty {
                out.append(PhonebookEntry(id: uid.isEmpty ? name : uid, name: name, numbers: nums))
            }
        }
        return out.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private static func unescape(_ s: String) -> String {
        s.replacingOccurrences(of: "\\,", with: ",").replacingOccurrences(of: "\;", with: ";")
            .replacingOccurrences(of: "\\n", with: " ").replacingOccurrences(of: "\\\\", with: "\\")
    }
}
