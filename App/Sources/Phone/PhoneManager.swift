import Foundation
import linphonesw

/// Bellen via de Linphone-bouwdoos (liblinphone). Eén account: het app-account van de gebruiker op de centrale.
/// Versie 0.1: bellen en gebeld worden terwijl de app open is. CallKit en push volgen in een volgende stap.
@MainActor
final class PhoneManager: ObservableObject {
    static let shared = PhoneManager()

    enum Status: Equatable {
        case off, connecting, registered, failed(String)

        var text: String {
            switch self {
            case .off: return "Niet aangemeld"
            case .connecting: return "Verbinden…"
            case .registered: return "Aangemeld"
            case .failed(let m): return m.isEmpty ? "Aanmelden mislukt" : "Aanmelden mislukt: \(m)"
            }
        }
    }

    enum CallPhase: Equatable {
        case none, incoming, outgoing, ringing, active, ended
    }

    @Published private(set) var status: Status = .off
    @Published private(set) var phase: CallPhase = .none
    @Published private(set) var remoteName = ""
    @Published private(set) var remoteNumber = ""
    @Published private(set) var connectedAt: Date?
    @Published var muted = false
    @Published var speaker = false

    private var core: Core?
    private var delegate: Delegate?
    private var domain = ""
    private var startedFor: ProvisioningConfig.Sip?

    // MARK: starten / stoppen

    func start(with cfg: ProvisioningConfig) {
        if startedFor == cfg.sip, core != nil { return }
        stop()
        do {
            let factory = Factory.Instance
            let core = try factory.createCore(configPath: "", factoryConfigPath: "", systemContext: nil)
            core.videoCaptureEnabled = false
            core.videoDisplayEnabled = false
            try core.start()

            let sip = cfg.sip
            domain = sip.port == 5060 ? sip.domain : "\(sip.domain):\(sip.port)"
            let auth = try factory.createAuthInfo(username: sip.username, userid: "", passwd: sip.password,
                                                  ha1: "", realm: "", domain: "")
            let params = try core.createAccountParams()
            try params.setIdentityaddress(newValue: try factory.createAddress(addr: "sip:\(sip.username)@\(domain)"))
            let server = try factory.createAddress(addr: "sip:\(domain)")
            try server.setTransport(newValue: sip.transport.lowercased() == "tcp" ? .Tcp : .Udp)
            try params.setServeraddress(newValue: server)
            params.registerEnabled = true
            let account = try core.createAccount(params: params)
            core.addAuthInfo(info: auth)
            try core.addAccount(account: account)
            core.defaultAccount = account

            let d = Delegate(owner: self)
            core.addDelegate(delegate: d)
            self.delegate = d
            self.core = core
            startedFor = cfg.sip
            status = .connecting
        } catch {
            status = .failed(error.localizedDescription)
        }
    }

    func stop() {
        if let core {
            if let d = delegate { core.removeDelegate(delegate: d) }
            core.clearAccounts()
            core.clearAllAuthInfo()
            core.stop()
        }
        core = nil
        delegate = nil
        startedFor = nil
        status = .off
        phase = .none
    }

    // MARK: bellen

    func call(_ number: String) {
        let digits = number.filter { "0123456789*#+".contains($0) }
        guard let core, !digits.isEmpty else { return }
        do {
            let addr = try Factory.Instance.createAddress(addr: "sip:\(digits)@\(domain)")
            let params = try core.createCallParams(call: nil)
            params.videoEnabled = false
            guard core.inviteAddressWithParams(addr: addr, params: params) != nil else { return }
            remoteNumber = digits
            remoteName = ""
            phase = .outgoing
        } catch {
            phase = .none
        }
    }

    func accept() {
        guard let call = core?.currentCall else { return }
        try? call.accept()
    }

    func hangup() {
        guard let core else { return }
        if let call = core.currentCall ?? core.calls.first {
            try? call.terminate()
        }
    }

    func toggleMute() {
        guard let core else { return }
        core.micEnabled.toggle()
        muted = !core.micEnabled
    }

    func toggleSpeaker() {
        guard let core, let call = core.currentCall else { return }
        speaker.toggle()
        let want: AudioDevice.Kind = speaker ? .Speaker : .Microphone
        if let dev = core.audioDevices.first(where: { $0.type == want }) {
            call.outputAudioDevice = dev
        } else if !speaker, let dev = core.audioDevices.first(where: { $0.type == .Earpiece }) {
            call.outputAudioDevice = dev
        }
    }

    func sendDtmf(_ c: Character) {
        guard let call = core?.currentCall, let v = c.asciiValue else { return }
        try? call.sendDtmf(dtmf: CChar(bitPattern: v))
    }

    // MARK: gebeurtenissen van liblinphone

    fileprivate func registrationChanged(_ state: RegistrationState, _ message: String) {
        switch state {
        case .Ok: status = .registered
        case .Progress, .Refreshing: if status != .registered { status = .connecting }
        case .Failed: status = .failed(message)
        case .Cleared, .None: status = .off
        @unknown default: break
        }
    }

    fileprivate func callChanged(_ call: Call, _ state: Call.State) {
        let addr = call.remoteAddress
        remoteNumber = addr?.username ?? remoteNumber
        remoteName = addr?.displayName ?? ""
        switch state {
        case .IncomingReceived: phase = .incoming
        case .OutgoingInit, .OutgoingProgress: phase = .outgoing
        case .OutgoingRinging, .OutgoingEarlyMedia: phase = .ringing
        case .Connected, .StreamsRunning:
            if phase != .active { connectedAt = Date() }
            phase = .active
        case .End, .Error, .Released:
            if core?.callsNb ?? 0 == 0 {
                phase = .ended
                connectedAt = nil
                muted = false
                speaker = false
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 1_200_000_000)
                    if self.phase == .ended { self.phase = .none }
                }
            }
        default: break
        }
    }
}

/// Ontvangt gebeurtenissen van liblinphone en geeft ze door aan de PhoneManager (op de hoofdthread).
private final class Delegate: CoreDelegate {
    weak var owner: PhoneManager?
    init(owner: PhoneManager) { self.owner = owner }

    func onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState, message: String) {
        Task { @MainActor in self.owner?.registrationChanged(state, message) }
    }

    func onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
        Task { @MainActor in self.owner?.callChanged(call, state) }
    }
}
