package org.verco.telefoon

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.MessageWaitingIndication
import org.linphone.core.RegistrationState
import org.linphone.core.SubscribePolicy
import org.linphone.core.TransportType

/** Bellen via de Linphone-bouwdoos (liblinphone). Eén account: het app-account op de centrale. */
class PhoneManager(private val context: Context, private val recents: RecentStore) {

    enum class Status { OFF, CONNECTING, REGISTERED, FAILED }
    enum class Phase { NONE, INCOMING, OUTGOING, RINGING, ACTIVE, ENDED }
    enum class Presence { UNKNOWN, AVAILABLE, BUSY, DND, OFFLINE }

    val status = MutableStateFlow(Status.OFF)
    val statusText = MutableStateFlow("Niet aangemeld")
    val phase = MutableStateFlow(Phase.NONE)
    val remoteName = MutableStateFlow("")
    val remoteNumber = MutableStateFlow("")
    val connectedAt = MutableStateFlow(0L)
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)
    val presence = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val voicemailNew = MutableStateFlow(0)

    var core: Core? = null
        private set
    private var domain = ""
    private var startedFor: String? = null
    private var friendList: FriendList? = null
    private var pushToken = ""
    private var pushProject = ""

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String) {
            when (state) {
                RegistrationState.Ok -> { status.value = Status.REGISTERED; statusText.value = "Aangemeld" }
                RegistrationState.Progress, RegistrationState.Refreshing ->
                    if (status.value != Status.REGISTERED) { status.value = Status.CONNECTING; statusText.value = "Verbinden…" }
                RegistrationState.Failed -> { status.value = Status.FAILED; statusText.value = "Aanmelden mislukt: $message" }
                else -> { status.value = Status.OFF; statusText.value = "Niet aangemeld" }
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            val addr = call.remoteAddress
            remoteNumber.value = addr.username ?: remoteNumber.value
            remoteName.value = addr.displayName ?: ""
            when (state) {
                Call.State.IncomingReceived -> {
                    phase.value = Phase.INCOMING
                    PhoneService.showIncoming(context, remoteName.value.ifEmpty { remoteNumber.value })
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> phase.value = Phase.OUTGOING
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> phase.value = Phase.RINGING
                Call.State.Connected, Call.State.StreamsRunning -> {
                    if (phase.value != Phase.ACTIVE) connectedAt.value = System.currentTimeMillis()
                    phase.value = Phase.ACTIVE
                    PhoneService.showOngoing(context, remoteName.value.ifEmpty { remoteNumber.value })
                }
                Call.State.Released -> {
                    remember(call)
                    ended()
                }
                Call.State.End, Call.State.Error -> ended()
                else -> {}
            }
        }

        override fun onNotifyPresenceReceived(core: Core, linphoneFriend: Friend) {
            val ext = linphoneFriend.address?.username ?: return
            presence.value = presence.value + (ext to when (linphoneFriend.consolidatedPresence) {
                ConsolidatedPresence.Online -> Presence.AVAILABLE
                ConsolidatedPresence.Busy -> Presence.BUSY
                ConsolidatedPresence.DoNotDisturb -> Presence.DND
                ConsolidatedPresence.Offline -> Presence.OFFLINE
                else -> Presence.UNKNOWN
            })
        }

        override fun onMessageWaitingIndicationChanged(core: Core, lev: org.linphone.core.Event, mwi: MessageWaitingIndication) {
            voicemailNew.value = (mwi.getSummary(MessageWaitingIndication.ContextClass.Voice)?.nbNew ?: mwi.nbNew)
        }
    }

    private fun ended() {
        if ((core?.callsNb ?: 0) == 0) {
            phase.value = Phase.ENDED
            connectedAt.value = 0
            muted.value = false
            speaker.value = false
            PhoneService.callFinished(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (phase.value == Phase.ENDED) phase.value = Phase.NONE
            }, 1200)
        }
    }

    private fun remember(call: Call) {
        val log = call.callLog
        val addr = log.remoteAddress
        val number = addr.username ?: ""
        if (number.startsWith("*")) return
        val kind = when {
            log.dir == Call.Dir.Outgoing -> "outgoing"
            log.status == Call.Status.Success || log.status == Call.Status.AcceptedElsewhere -> "incoming"
            else -> "missed"
        }
        recents.add(RecentCall(kind, number, addr.displayName ?: "", log.startDate * 1000L, log.duration))
    }

    // ------------------------------------------------------------ starten / stoppen

    fun start(cfg: ProvisioningConfig) {
        val key = cfg.sipUser + "|" + cfg.server
        if (startedFor == key && core != null) return
        stop()
        try {
            val factory = Factory.instance()
            val c = factory.createCore(null, null, context)
            c.isVideoCaptureEnabled = false
            c.isVideoDisplayEnabled = false
            // push regelen we zelf (Push.kt); de eigen Firebase-koppeling van de bouwdoos blijft uit
            c.isPushNotificationEnabled = false
            c.start()
            domain = cfg.server
            val auth = factory.createAuthInfo(cfg.sipUser, null, cfg.sipPassword, null, null, null, null)
            val params = c.createAccountParams()
            params.identityAddress = factory.createAddress("sip:${cfg.sipUser}@$domain")
            val server = factory.createAddress("sip:$domain")
            server?.transport = if (cfg.transport.equals("tcp", true)) TransportType.Tcp else TransportType.Udp
            params.serverAddress = server
            params.isRegisterEnabled = true
            params.pushNotificationAllowed = false
            params.remotePushNotificationAllowed = false
            pushContact()?.let { params.contactUriParameters = it }
            if (cfg.voicemail.isNotEmpty()) params.mwiServerAddress = factory.createAddress("sip:${cfg.sipUser}@$domain")
            val account = c.createAccount(params)
            c.addAuthInfo(auth)
            c.addAccount(account)
            c.defaultAccount = account
            c.addListener(listener)
            core = c
            startedFor = key
            status.value = Status.CONNECTING
            statusText.value = "Verbinden…"
            watchColleagues(cfg.colleagues, c)
        } catch (e: Exception) {
            status.value = Status.FAILED
            statusText.value = "Aanmelden mislukt: ${e.message}"
        }
    }

    /** Pushsleutel van Firebase: meesturen bij het aanmelden (RFC 8599), zodat de centrale de app kan wekken. */
    fun setPushToken(token: String, project: String) {
        if (token == pushToken && project == pushProject) return
        pushToken = token
        pushProject = project
        val acc = core?.defaultAccount ?: return
        val p = acc.params.clone()
        p.contactUriParameters = pushContact()
        acc.params = p
    }

    private fun pushContact(): String? =
        if (pushToken.isEmpty()) null else "pn-provider=fcm;pn-prid=$pushToken;pn-param=$pushProject"

    /** Gewekt door een pushbericht: meteen opnieuw aanmelden, dan komt het gesprek binnen. */
    fun wake(cfg: ProvisioningConfig) {
        if (core == null) start(cfg)
        core?.ensureRegistered()
        core?.refreshRegisters()
    }

    fun stop() {
        core?.let {
            it.removeListener(listener)
            it.clearAccounts()
            it.clearAllAuthInfo()
            it.stop()
        }
        core = null
        startedFor = null
        friendList = null
        presence.value = emptyMap()
        voicemailNew.value = 0
        status.value = Status.OFF
        statusText.value = "Niet aangemeld"
        phase.value = Phase.NONE
    }

    private fun watchColleagues(list: List<Colleague>, c: Core) {
        if (list.isEmpty()) return
        val fl = c.createFriendList()
        fl.displayName = "Collega's"
        val start = mutableMapOf<String, Presence>()
        for (col in list) {
            val f = c.createFriend()
            val addr = Factory.instance().createAddress("sip:${col.ext}@$domain") ?: continue
            f.name = col.name
            f.addAddress(addr)
            f.isSubscribesEnabled = true
            f.incSubscribePolicy = SubscribePolicy.SPDeny
            fl.addFriend(f)
            start[col.ext] = Presence.UNKNOWN
        }
        c.addFriendList(fl)
        fl.isSubscriptionsEnabled = true
        fl.updateSubscriptions()
        friendList = fl
        presence.value = start
    }

    // ------------------------------------------------------------ bellen

    fun call(number: String) {
        val digits = number.filter { it in "0123456789*#+" }
        val c = core ?: return
        if (digits.isEmpty() || (phase.value != Phase.NONE && phase.value != Phase.ENDED)) return
        val addr = Factory.instance().createAddress("sip:$digits@$domain") ?: return
        val params = c.createCallParams(null) ?: return
        params.isVideoEnabled = false
        remoteNumber.value = digits
        remoteName.value = ""
        phase.value = Phase.OUTGOING
        if (c.inviteAddressWithParams(addr, params) == null) phase.value = Phase.NONE
    }

    fun accept() { (core?.currentCall ?: core?.calls?.firstOrNull())?.accept() }

    fun hangup() {
        val c = core ?: return
        (c.currentCall ?: c.calls.firstOrNull())?.terminate() ?: run { phase.value = Phase.NONE }
    }

    fun toggleMute() {
        val c = core ?: return
        c.isMicEnabled = !c.isMicEnabled
        muted.value = !c.isMicEnabled
    }

    fun toggleSpeaker() {
        val c = core ?: return
        val call = c.currentCall ?: return
        speaker.value = !speaker.value
        val want = if (speaker.value) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        c.audioDevices.firstOrNull { it.type == want }?.let { call.outputAudioDevice = it }
    }

    fun dtmf(ch: Char) { core?.currentCall?.sendDtmf(ch) }

    fun transfer(number: String): Boolean {
        val digits = number.filter { it in "0123456789*#+" }
        val call = core?.currentCall ?: return false
        val addr = Factory.instance().createAddress("sip:$digits@$domain") ?: return false
        return call.transferTo(addr) == 0
    }
}
