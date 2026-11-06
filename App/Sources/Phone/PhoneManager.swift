import Foundation
import linphonesw

/// Bellen via de Linphone-bouwdoos (liblinphone). Eén account: het app-account van de gebruiker op de centrale.
/// Gesprekken lopen via CallKit (CallKitManager): iOS toont ze als gewoon gesprek. Push volgt in een volgende stap.
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

    /// Lampje van een collega (via presence op zijn toestelnummer).
    enum Presence: Equatable {
        case unknown, available, busy, dnd, offline
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
    /// Lampjes van collega's, per toestelnummer.
    @Published private(set) var presence: [String: Presence] = [:]
    /// Aantal nieuwe voicemailberichten (MWI van de centrale).
    @Published private(set) var voicemailNew = 0
    private var accountDelegate: AccountWatcher?
    private var voicemailCode = ""
    private var friendList: FriendList?

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
            core.callkitEnabled = true          // geluid wordt geregeld via CallKit (activateAudio)
            CallKitManager.shared.phone = self
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
            voicemailCode = cfg.voicemail
            if !cfg.voicemail.isEmpty {
                // voicemailteller: abonnement op 'message-summary' bij de centrale
                params.mwiServerAddress = try? factory.createAddress(addr: "sip:\(sip.username)@\(domain)")
            }
            let account = try core.createAccount(params: params)
            let aw = AccountWatcher(owner: self)
            account.addDelegate(delegate: aw)
            accountDelegate = aw
            core.addAuthInfo(info: auth)
            try core.addAccount(account: account)
            core.defaultAccount = account

            let d = Delegate(owner: self)
            core.addDelegate(delegate: d)
            self.delegate = d
            self.core = core
            startedFor = cfg.sip
            status = .connecting
            watchColleagues(cfg.colleagues ?? [], core: core)
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
        friendList = nil
        presence = [:]
        accountDelegate = nil
        voicemailNew = 0
        startedFor = nil
        status = .off
        phase = .none
    }

    // MARK: bellen

    /// Bellen vanuit de app: via CallKit.
    func call(_ number: String) {
        let digits = number.filter { "0123456789*#+".contains($0) }
        guard core != nil, !digits.isEmpty, phase == .none || phase == .ended else { return }
        remoteNumber = digits
        remoteName = ""
        phase = .outgoing
        CallKitManager.shared.startOutgoing(number: digits)
    }

    /// Het gesprek echt opzetten (aangeroepen door CallKit).
    func placeCall(_ number: String) {
        guard let core else { return }
        do {
            let addr = try Factory.Instance.createAddress(addr: "sip:\(number)@\(domain)")
            let params = try core.createCallParams(call: nil)
            params.videoEnabled = false
            if core.inviteAddressWithParams(addr: addr, params: params) == nil {
                phase = .none
                CallKitManager.shared.callEnded(failed: true)
            }
        } catch {
            phase = .none
            CallKitManager.shared.callEnded(failed: true)
        }
    }

    func accept() { CallKitManager.shared.requestAnswer() }
    func hangup() { CallKitManager.shared.requestEnd() }

    func acceptDirect() {
        guard let call = core?.currentCall ?? core?.calls.first else { return }
        try? call.accept()
    }

    func hangupDirect() {
        guard let core else { return }
        if let call = core.currentCall ?? core.calls.first {
            try? call.terminate()
        } else if phase != .none {
            phase = .none
        }
    }

    /// Lopend gesprek doorverbinden naar een ander nummer (zonder overleg).
    func transfer(to number: String) -> Bool {
        let digits = number.filter { "0123456789*#+".contains($0) }
        guard let call = core?.currentCall, !digits.isEmpty,
              let addr = try? Factory.Instance.createAddress(addr: "sip:\(digits)@\(domain)") else { return false }
        do {
            try call.transferTo(referTo: addr)
            return true
        } catch {
            return false
        }
    }

    func configureAudio() { core?.configureAudioSession() }
    func activateAudio(_ on: Bool) { core?.activateAudioSession(activated: on) }

    func toggleMute() { CallKitManager.shared.requestMute(!muted) }

    func setMutedDirect(_ on: Bool) {
        guard let core else { return }
        core.micEnabled = !on
        muted = on
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

    // MARK: lampjes van collega's

    /// Voor elke collega een abonnement op zijn status (SUBSCRIBE presence bij de centrale).
    private func watchColleagues(_ list: [ProvisioningConfig.Colleague], core: Core) {
        guard !list.isEmpty, let fl = try? core.createFriendList() else { return }
        fl.displayName = "Collega's"
        for c in list {
            guard let f = try? core.createFriend(),
                  let addr = try? Factory.Instance.createAddress(addr: "sip:\(c.ext)@\(domain)") else { continue }
            try? f.setName(newValue: c.name)
            f.addAddress(address: addr)
            try? f.setSubscribesenabled(newValue: true)
            try? f.setIncsubscribepolicy(newValue: .SPDeny)
            _ = fl.addFriend(linphoneFriend: f)
            presence[c.ext] = .unknown
        }
        core.addFriendList(list: fl)
        fl.subscriptionsEnabled = true
        fl.updateSubscriptions()
        friendList = fl
    }

    fileprivate func presenceChanged(_ friend: Friend) {
        guard let ext = friend.address?.username else { return }
        switch friend.consolidatedPresence {
        case .Online: presence[ext] = .available
        case .Busy: presence[ext] = .busy
        case .DoNotDisturb: presence[ext] = .dnd
        case .Offline: presence[ext] = .offline
        @unknown default: presence[ext] = .unknown
        }
    }

    fileprivate func voicemailChanged(_ mwi: MessageWaitingIndication) {
        voicemailNew = Int(mwi.getSummary(contextClass: .Voice)?.nbNew ?? mwi.nbNew)
    }

    /// Gesprek bewaren in Recent (na afloop).
    private func remember(_ call: Call) {
        guard let log = call.callLog else { return }
        let addr = log.remoteAddress
        let number = addr?.username ?? ""
        if number.hasPrefix("*") { return }              // functiecodes (*97, *78…) niet in Recent
        let kind: RecentCall.Kind
        if log.dir == .Outgoing {
            kind = .outgoing
        } else {
            kind = log.status == .Success || log.status == .AcceptedElsewhere ? .incoming : .missed
        }
        RecentStore.shared.add(RecentCall(kind: kind, number: number, name: addr?.displayName ?? "",
                                          date: Date(timeIntervalSince1970: TimeInterval(log.startDate)),
                                          duration: log.duration))
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
        case .IncomingReceived:
            phase = .incoming
            CallKitManager.shared.reportIncoming(name: remoteName, number: remoteNumber)
        case .OutgoingInit, .OutgoingProgress: phase = .outgoing
        case .OutgoingRinging, .OutgoingEarlyMedia: phase = .ringing
        case .Connected, .StreamsRunning:
            if phase != .active {
                connectedAt = Date()
                CallKitManager.shared.callConnected()
            }
            phase = .active
        case .Released:
            remember(call)
            fallthrough
        case .End, .Error:
            if core?.callsNb ?? 0 == 0 {
                CallKitManager.shared.callEnded(failed: state == .Error)
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

    func onNotifyPresenceReceived(core: Core, linphoneFriend: Friend) {
        Task { @MainActor in self.owner?.presenceChanged(linphoneFriend) }
    }

    func onMessageWaitingIndicationChanged(core: Core, lev: Event, mwi: MessageWaitingIndication) {
        Task { @MainActor in self.owner?.voicemailChanged(mwi) }
    }
}

/// Voicemailteller per account (ook als de centrale de melding uit zichzelf stuurt).
private final class AccountWatcher: AccountDelegate {
    weak var owner: PhoneManager?
    init(owner: PhoneManager) { self.owner = owner }

    func onMessageWaitingIndicationChanged(account: Account, mwi: MessageWaitingIndication) {
        Task { @MainActor in self.owner?.voicemailChanged(mwi) }
    }
}
