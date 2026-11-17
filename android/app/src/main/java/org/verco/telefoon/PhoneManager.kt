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
    /** Vooraf gekozen luidspreker (op het belscherm): een uitgaand gesprek start dan meteen op de speaker. */
    val speakerPreset = MutableStateFlow(
        context.getSharedPreferences("verco", Context.MODE_PRIVATE).getBoolean("speakerPreset", false)
    )
    /** Of het huidige gesprek beeld (video) heeft. */
    val videoActive = MutableStateFlow(false)
    private var localHangup = false
    private var finishing = false
    val presence = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val voicemailNew = MutableStateFlow(0)

    var core: Core? = null
        private set
    private var domain = ""
    private var startedFor: String? = null
    private var friendList: FriendList? = null
    private var pushToken = ""
    private var pushProject = ""
    private var ringtone: android.media.Ringtone? = null

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
                    finishing = false
                    phase.value = Phase.INCOMING
                    PhoneService.showIncoming(context, remoteName.value.ifEmpty { remoteNumber.value })
                    startRinging()
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> phase.value = Phase.OUTGOING
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> phase.value = Phase.RINGING
                Call.State.Connected, Call.State.StreamsRunning -> {
                    stopRinging()
                    if (phase.value != Phase.ACTIVE) connectedAt.value = System.currentTimeMillis()
                    phase.value = Phase.ACTIVE
                    if (state == Call.State.StreamsRunning && speakerPreset.value && !speaker.value) applySpeaker(true)
                    videoActive.value = runCatching { call.currentParams.isVideoEnabled }.getOrDefault(false)
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
            val p = when (linphoneFriend.consolidatedPresence) {
                ConsolidatedPresence.Online -> Presence.AVAILABLE
                ConsolidatedPresence.Busy -> Presence.BUSY
                ConsolidatedPresence.DoNotDisturb -> Presence.DND
                ConsolidatedPresence.Offline -> Presence.OFFLINE
                else -> Presence.UNKNOWN
            }
            // Een leeg/beëindigend bericht (UNKNOWN) mag een eerder bekende status niet op grijs gooien.
            if (p == Presence.UNKNOWN && presence.value[ext]?.let { it != Presence.UNKNOWN } == true) return
            presence.value = presence.value + (ext to p)
        }

        override fun onMessageWaitingIndicationChanged(core: Core, lev: org.linphone.core.Event, mwi: MessageWaitingIndication) {
            voicemailNew.value = (mwi.getSummary(MessageWaitingIndication.ContextClass.Voice)?.nbNew ?: mwi.nbNew)
        }
    }

    private fun ended() {
        stopRinging()
        if ((core?.callsNb ?: 0) != 0) return
        if (finishing) return          // kogelvrij: End én Released vuren allebei -> maar één keer afhandelen
        finishing = true
        connectedAt.value = 0
        muted.value = false
        speaker.value = false
        videoActive.value = false
        runCatching { core?.nativeVideoWindowId = null; core?.nativePreviewWindowId = null }
        PhoneService.callFinished(context)
        if (localHangup) {
            // jij hing zelf op: direct terug naar de app, geen "beëindigd"-scherm erachteraan
            localHangup = false
            phase.value = Phase.NONE
        } else {
            // de ander hing op: kort tonen waarom het gesprek sloot
            phase.value = Phase.ENDED
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (phase.value == Phase.ENDED) phase.value = Phase.NONE
            }, 700)
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
        // Sleutel bevat domein/poort/transport: zo verbindt de app opnieuw als het netwerk wisselt
        // (kantoor-wifi ↔ tussenstation) of als het transport verandert (UDP ↔ TCP).
        val key = "${cfg.sipUser}|${cfg.domain}|${cfg.port}|${cfg.transport}"
        if (startedFor == key && core != null) return
        stop()
        try {
            val factory = Factory.instance()
            val c = factory.createCore(null, null, context)
            c.isVideoCaptureEnabled = true
            c.isVideoDisplayEnabled = true
            runCatching {
                val vp = c.videoActivationPolicy
                vp.automaticallyAccept = true      // inkomend videogesprek met beeld aannemen
                vp.automaticallyInitiate = false   // zelf alleen beeld als de gebruiker het kiest
                c.videoActivationPolicy = vp
            }
            // IPv4 afdwingen: op mobiel internet (IPv6) zou de audio naar een IPv6-adres gaan dat het
            // tussenstation (IPv4) niet kan bereiken -> geen geluid. Met IPv6 uit loopt alles over IPv4.
            c.isIpv6Enabled = false
            // push regelen we zelf (Push.kt); de eigen Firebase-koppeling van de bouwdoos blijft uit
            c.isPushNotificationEnabled = false
            // toetsdruk tijdens gesprek (DTMF): RTP-events (rfc2833/4733) + SIP INFO als terugval
            runCatching { c.useRfc2833ForDtmf = true; c.useInfoForDtmf = true }
            // Vangnet: als de verbinding/BYE wegvalt (NAT over de relay of extern netwerk weg),
            // sluit de app het gesprek zelf na 10s stilte i.p.v. te blijven hangen.
            // Niet lager: een korte 4G-hik mag een lopend gesprek niet verbreken.
            runCatching { c.nortpTimeout = 10 }
            c.start()
            // altijd de FRONTCAMERA forceren bij opstarten (en nooit het "StaticImage"-nepcamera-apparaat)
            runCatching {
                val devs = c.videoDevicesList
                val real = devs.filter { !it.contains("StaticImage", true) && !it.contains("Static picture", true) }
                // frontcamera-id opzoeken via Android Camera2 (LENS_FACING_FRONT) en matchen op de liblinphone-naam
                val frontId = (context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager)?.let { cm ->
                    runCatching {
                        cm.cameraIdList.firstOrNull { id ->
                            cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                        }
                    }.getOrNull()
                }
                val cam = (frontId?.let { fid -> real.firstOrNull { it.trim().endsWith(fid) } })
                    ?: real.firstOrNull { it.contains("Front", true) }
                    ?: real.firstOrNull()
                if (cam != null) c.videoDevice = cam
            }
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

    /** Inkomend gesprek: doorlopend overgaan (belgeluid + trillen) tot opgenomen of beëindigd. */
    private fun startRinging() {
        stopRinging()
        runCatching {
            val rm = android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.RingtoneManager.getRingtone(context, rm)?.apply {
                audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31)
                (context.getSystemService(android.os.VibratorManager::class.java))?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(android.os.Vibrator::class.java)
            val pattern = longArrayOf(0, 800, 1000)
            if (android.os.Build.VERSION.SDK_INT >= 26)
                v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") v?.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31)
                (context.getSystemService(android.os.VibratorManager::class.java))?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(android.os.Vibrator::class.java)
            v?.cancel()
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
        // Forceer een nieuwe aanmelding zodat de push-gegevens meteen bij de centrale staan
        // (anders blijft de app aangemeld zónder push en kan 'ie bij zwart scherm niet gewekt worden).
        core?.refreshRegisters()
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

    /** Ververs de presence-abonnementen zodat de bolletjes snel weer kloppen (bv. na een gesprek). */
    fun refreshPresence() {
        if (phase.value != Phase.NONE) return   // niet verversen als er (alweer) een gesprek loopt
        runCatching { friendList?.updateSubscriptions() }
    }

    // ------------------------------------------------------------ bellen

    fun call(number: String, video: Boolean = false) {
        val digits = number.filter { it in "0123456789*#+" }
        val c = core ?: return
        if (digits.isEmpty() || (phase.value != Phase.NONE && phase.value != Phase.ENDED)) return
        localHangup = false
        finishing = false
        val addr = Factory.instance().createAddress("sip:$digits@$domain") ?: return
        val params = c.createCallParams(null) ?: return
        params.isVideoEnabled = video
        videoActive.value = video
        remoteNumber.value = digits
        remoteName.value = ""
        phase.value = Phase.OUTGOING
        if (c.inviteAddressWithParams(addr, params) == null) phase.value = Phase.NONE
    }

    fun accept() { localHangup = false; (core?.currentCall ?: core?.calls?.firstOrNull())?.accept() }

    fun hangup() {
        val c = core ?: return
        localHangup = true
        (c.currentCall ?: c.calls.firstOrNull())?.terminate() ?: run { phase.value = Phase.NONE }
    }

    fun toggleMute() {
        val c = core ?: return
        c.isMicEnabled = !c.isMicEnabled
        muted.value = !c.isMicEnabled
    }

    fun toggleSpeaker() { applySpeaker(!speaker.value) }

    /** Zet de audio op luidspreker of oorstuk voor het lopende gesprek. */
    private fun applySpeaker(on: Boolean) {
        val c = core ?: return
        val call = c.currentCall ?: return
        speaker.value = on
        val want = if (on) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        c.audioDevices.firstOrNull { it.type == want }?.let { call.outputAudioDevice = it }
    }

    /** Vooraf de luidspreker aan/uit zetten (op het belscherm); meteen toepassen als er al een gesprek loopt. */
    fun setSpeakerPreset(on: Boolean) {
        speakerPreset.value = on
        runCatching { context.getSharedPreferences("verco", Context.MODE_PRIVATE).edit().putBoolean("speakerPreset", on).apply() }
        if (core?.currentCall != null) applySpeaker(on)
    }

    /** Koppel het schermoppervlak waarop het beeld van de ander wordt getoond. */
    fun setVideoWindow(view: Any?) { runCatching { core?.nativeVideoWindowId = view } }
    /** Koppel het schermoppervlak voor het eigen (voorbeeld-)camerabeeld. */
    fun setPreviewWindow(view: Any?) { runCatching { core?.nativePreviewWindowId = view } }

    /** Beeld aan/uit zetten tijdens het gesprek. */
    fun toggleVideo() {
        val c = core ?: return
        val call = c.currentCall ?: return
        val want = !runCatching { call.currentParams.isVideoEnabled }.getOrDefault(false)
        val params = c.createCallParams(call) ?: return
        params.isVideoEnabled = want
        runCatching { call.update(params) }
        videoActive.value = want
    }

    /** Wisselen tussen voor- en achtercamera. */
    fun switchCamera() {
        val c = core ?: return
        val devs = c.videoDevicesList
        val cur = c.videoDevice
        val next = devs.firstOrNull { it != cur && !it.contains("StaticImage", true) }
        if (next != null) c.videoDevice = next
    }

    fun dtmf(ch: Char) {
        core?.currentCall?.sendDtmf(ch)
        playKeyTone(ch)
    }

    // Kort DTMF-toontje lokaal afspelen als hoorbare bevestiging bij een toetsdruk.
    private val toneGen by lazy {
        runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 70) }.getOrNull()
    }
    fun playKeyTone(ch: Char) {
        val tone = when (ch) {
            '0' -> android.media.ToneGenerator.TONE_DTMF_0
            '1' -> android.media.ToneGenerator.TONE_DTMF_1
            '2' -> android.media.ToneGenerator.TONE_DTMF_2
            '3' -> android.media.ToneGenerator.TONE_DTMF_3
            '4' -> android.media.ToneGenerator.TONE_DTMF_4
            '5' -> android.media.ToneGenerator.TONE_DTMF_5
            '6' -> android.media.ToneGenerator.TONE_DTMF_6
            '7' -> android.media.ToneGenerator.TONE_DTMF_7
            '8' -> android.media.ToneGenerator.TONE_DTMF_8
            '9' -> android.media.ToneGenerator.TONE_DTMF_9
            '*' -> android.media.ToneGenerator.TONE_DTMF_S
            '#' -> android.media.ToneGenerator.TONE_DTMF_P
            else -> return
        }
        runCatching { toneGen?.startTone(tone, 150) }
    }

    fun transfer(number: String): Boolean {
        val digits = number.filter { it in "0123456789*#+" }
        val call = core?.currentCall ?: return false
        val addr = Factory.instance().createAddress("sip:$digits@$domain") ?: return false
        return call.transferTo(addr) == 0
    }
}
