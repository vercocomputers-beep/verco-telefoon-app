import Foundation

/// Belgeschiedenis van de app (laatste 100 gesprekken), bewaard op de telefoon.
struct RecentCall: Codable, Identifiable, Hashable {
    enum Kind: String, Codable { case incoming, outgoing, missed }
    var id = UUID()
    var kind: Kind
    var number: String
    var name: String
    var date: Date
    var duration: Int
}

@MainActor
final class RecentStore: ObservableObject {
    static let shared = RecentStore()

    @Published private(set) var calls: [RecentCall] = []
    /// Gemiste gesprekken die nog niet bekeken zijn (rood getal bij Recent).
    @Published var unseenMissed = 0

    private let key = "org.verco.telefoon.recent"

    private init() {
        if let data = UserDefaults.standard.data(forKey: key),
           let list = try? JSONDecoder().decode([RecentCall].self, from: data) {
            calls = list
        }
    }

    func add(_ call: RecentCall) {
        guard !call.number.isEmpty else { return }
        calls.insert(call, at: 0)
        if calls.count > 100 { calls.removeLast(calls.count - 100) }
        if call.kind == .missed { unseenMissed += 1 }
        save()
    }

    func clear() {
        calls = []
        unseenMissed = 0
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(calls) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}
