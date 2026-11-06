import Foundation

/// Instellingen die de centrale geeft op /prov/vt (versie 1).
struct ProvisioningConfig: Codable, Equatable {
    struct User: Codable, Equatable {
        var name: String
        var ext: String
        enum CodingKeys: String, CodingKey {
            case name
            case ext = "extension"
        }
    }

    struct Sip: Codable, Equatable {
        var username: String
        var password: String
        var domain: String
        var port: Int
        var transport: String
    }

    struct Function: Codable, Equatable, Identifiable, Hashable {
        var name: String
        var code: String
        var id: String { code + name }
    }

    struct Push: Codable, Equatable {
        var enabled: Bool
    }

    var version: Int
    var company: String
    var user: User
    var sip: Sip
    var voicemail: String
    var video: Bool
    var codecs: [String]
    var phonebook: String
    var functions: [Function]
    var push: Push?

    /// Server waar de instellingen vandaan kwamen (om later te verversen).
    var source: String?
}

/// De link uit de QR-code: verco-telefoon://setup?s=<centrale>&u=<gebruiker>&p=<wachtwoord>
struct SetupLink {
    let server: URL
    let username: String
    let password: String

    init?(url: URL) {
        guard url.scheme?.lowercased() == "verco-telefoon", url.host?.lowercased() == "setup",
              let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        func value(_ name: String) -> String? {
            comps.queryItems?.first(where: { $0.name == name })?.value
        }
        guard let s = value("s"), let server = URL(string: s), let u = value("u"), let p = value("p"),
              !u.isEmpty, !p.isEmpty else { return nil }
        self.server = server
        self.username = u
        self.password = p
    }

    init(server: URL, username: String, password: String) {
        self.server = server
        self.username = username
        self.password = password
    }

    var configURL: URL {
        var comps = URLComponents(url: server.appendingPathComponent("prov/vt"), resolvingAgainstBaseURL: false)!
        comps.queryItems = [URLQueryItem(name: "u", value: username), URLQueryItem(name: "p", value: password)]
        return comps.url!
    }
}
