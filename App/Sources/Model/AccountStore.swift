import Foundation
import Security

/// Bewaart de instellingen van de centrale veilig (sleutelhanger) en haalt ze op via de QR-link.
@MainActor
final class AccountStore: ObservableObject {
    static let shared = AccountStore()

    @Published private(set) var config: ProvisioningConfig?
    @Published var busy = false
    @Published var error: String?

    private let keychainKey = "org.verco.telefoon.config"

    private init() {
        if let data = Keychain.read(keychainKey),
           let cfg = try? JSONDecoder().decode(ProvisioningConfig.self, from: data) {
            config = cfg
        }
    }

    /// Link uit de QR-code (camera of scanner in de app).
    func setup(from url: URL) async {
        guard let link = SetupLink(url: url) else {
            error = "Dit is geen QR-code van de telefooncentrale."
            return
        }
        await setup(link: link)
    }

    func setup(link: SetupLink) async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            var req = URLRequest(url: link.configURL, timeoutInterval: 15)
            req.setValue("application/json", forHTTPHeaderField: "Accept")
            let (data, resp) = try await URLSession.shared.data(for: req)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            guard code == 200 else {
                let msg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? String
                throw SetupError.server(msg ?? "De centrale antwoordt met fout \(code).")
            }
            var cfg = try JSONDecoder().decode(ProvisioningConfig.self, from: data)
            guard cfg.version == 1 else { throw SetupError.server("Deze app is te oud voor de centrale. Werk de app bij.") }
            cfg.source = link.server.absoluteString
            try save(cfg)
            config = cfg
        } catch let e as SetupError {
            error = e.text
        } catch let e as URLError where e.code == .notConnectedToInternet || e.code == .cannotConnectToHost
                    || e.code == .timedOut || e.code == .cannotFindHost {
            error = "De centrale is niet bereikbaar. Zit je op de wifi van kantoor?"
        } catch {
            self.error = "Instellen mislukt: \(error.localizedDescription)"
        }
    }

    /// Instellingen opnieuw ophalen bij dezelfde centrale (bijv. nieuwe collega's of ander adres).
    func refresh(showErrors: Bool = false) async {
        guard let cfg = config, let src = cfg.source, let server = URL(string: src) else { return }
        let before = error
        await setup(link: SetupLink(server: server, username: cfg.sip.username, password: cfg.sip.password))
        if !showErrors { error = before }
    }

    func logout() {
        Keychain.delete(keychainKey)
        config = nil
    }

    private func save(_ cfg: ProvisioningConfig) throws {
        try Keychain.write(keychainKey, JSONEncoder().encode(cfg))
    }
}

enum SetupError: Error {
    case server(String)
    var text: String {
        switch self { case .server(let s): return s }
    }
}

enum Keychain {
    static func read(_ key: String) -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key,
                                kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        return SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess ? out as? Data : nil
    }

    static func write(_ key: String, _ data: Data) throws {
        delete(key)
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key,
                                kSecValueData as String: data,
                                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemAdd(q as CFDictionary, nil)
        if status != errSecSuccess { throw SetupError.server("Opslaan in de sleutelhanger mislukt (\(status)).") }
    }

    static func delete(_ key: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key]
        SecItemDelete(q as CFDictionary)
    }
}
