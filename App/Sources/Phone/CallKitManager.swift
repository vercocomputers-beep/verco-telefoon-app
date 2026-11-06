import AVFoundation
import CallKit
import Foundation

/// Koppeling met CallKit: gesprekken verschijnen als gewoon iPhone-gesprek (ook op het vergrendelscherm,
/// in Recent en met de knoppen van iOS). Alle acties lopen via CallKit; de PhoneManager voert ze uit.
final class CallKitManager: NSObject, CXProviderDelegate {
    static let shared = CallKitManager()

    private let provider: CXProvider
    private let controller = CXCallController()
    /// CallKit-nummer van het lopende gesprek (de app ondersteunt één gesprek tegelijk).
    private(set) var currentUUID: UUID?
    private var outgoing = false
    weak var phone: PhoneManager?

    override init() {
        let cfg = CXProviderConfiguration()
        cfg.supportsVideo = false
        cfg.maximumCallGroups = 1
        cfg.maximumCallsPerCallGroup = 1
        cfg.supportedHandleTypes = [.phoneNumber, .generic]
        cfg.includesCallsInRecents = true
        provider = CXProvider(configuration: cfg)
        super.init()
        provider.setDelegate(self, queue: nil)   // nil = hoofdthread
    }

    // MARK: verzoeken vanuit de app

    /// Uitgaand gesprek starten via CallKit. Lukt dat niet (bijv. in de simulator), dan direct bellen.
    func startOutgoing(number: String) {
        let uuid = UUID()
        let action = CXStartCallAction(call: uuid, handle: CXHandle(type: .phoneNumber, value: number))
        action.isVideo = false
        currentUUID = uuid
        outgoing = true
        controller.request(CXTransaction(action: action)) { [weak self] error in
            guard error != nil else { return }
            self?.currentUUID = nil
            Task { @MainActor in self?.phone?.placeCall(number) }
        }
    }

    /// Inkomend gesprek melden aan iOS (iOS laat de telefoon overgaan).
    func reportIncoming(name: String, number: String) {
        let uuid = UUID()
        currentUUID = uuid
        outgoing = false
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: number)
        update.localizedCallerName = name.isEmpty ? number : name
        update.hasVideo = false
        update.supportsDTMF = true
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false
        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            guard error != nil else { return }
            // iOS wil het gesprek niet tonen (bijv. Niet storen of simulator): de app toont het zelf.
            self?.currentUUID = nil
        }
    }

    func requestAnswer() {
        guard let uuid = currentUUID else {
            Task { @MainActor in self.phone?.acceptDirect() }
            return
        }
        controller.request(CXTransaction(action: CXAnswerCallAction(call: uuid))) { _ in }
    }

    func requestEnd() {
        guard let uuid = currentUUID else {
            Task { @MainActor in self.phone?.hangupDirect() }
            return
        }
        controller.request(CXTransaction(action: CXEndCallAction(call: uuid))) { [weak self] error in
            if error != nil {
                self?.currentUUID = nil
                Task { @MainActor in self?.phone?.hangupDirect() }
            }
        }
    }

    func requestMute(_ muted: Bool) {
        guard let uuid = currentUUID else {
            Task { @MainActor in self.phone?.setMutedDirect(muted) }
            return
        }
        controller.request(CXTransaction(action: CXSetMutedCallAction(call: uuid, muted: muted))) { _ in }
    }

    // MARK: meldingen vanuit de PhoneManager

    func callConnected() {
        guard let uuid = currentUUID, outgoing else { return }
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    func callEnded(failed: Bool) {
        guard let uuid = currentUUID else { return }
        currentUUID = nil
        provider.reportCall(with: uuid, endedAt: Date(), reason: failed ? .failed : .remoteEnded)
    }

    // MARK: CXProviderDelegate (iOS voert een actie uit)

    func providerDidReset(_ provider: CXProvider) {
        currentUUID = nil
        Task { @MainActor in self.phone?.hangupDirect() }
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        let number = action.handle.value
        Task { @MainActor in
            self.phone?.configureAudio()
            self.phone?.placeCall(number)
            provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        Task { @MainActor in
            self.phone?.configureAudio()
            self.phone?.acceptDirect()
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        if currentUUID == action.callUUID { currentUUID = nil }
        Task { @MainActor in
            self.phone?.hangupDirect()
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        let muted = action.isMuted
        Task { @MainActor in
            self.phone?.setMutedDirect(muted)
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXPlayDTMFCallAction) {
        let digits = action.digits
        Task { @MainActor in
            for c in digits { self.phone?.sendDtmf(c) }
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        Task { @MainActor in self.phone?.activateAudio(true) }
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        Task { @MainActor in self.phone?.activateAudio(false) }
    }
}
