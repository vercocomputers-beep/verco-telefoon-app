package org.verco.telefoon

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

/**
 * Vangt een uitgaand gesprek vanuit de telefoon-contacten op en kiest Zaak of Privé.
 * - Privé  -> het gesprek gaat ongewijzigd door over de mobiele (sim) lijn.
 * - Zaak   -> het mobiele gesprek wordt geannuleerd en Verco belt via de centrale (SIP).
 * - Vragen -> er verschijnt een keuzeschermpje (CallChoiceActivity).
 *
 * Werkt alleen als de gebruiker Verco de rol "gesprekken omleiden" heeft gegeven (Instellingen).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class VercoCallRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean,
    ) {
        val number = handle.schemeSpecificPart?.filter { it in "0123456789*#+" } ?: ""
        if (number.isEmpty()) { placeCallUnmodified(); return }

        when (CallRouting.mode(this)) {
            CallMode.PRIVE -> placeCallUnmodified()
            CallMode.ZAAK -> { cancelCall(); callViaVerco(number) }
            CallMode.ASK -> {
                if (!allowInteractiveResponse) { placeCallUnmodified(); return }
                arm(this, number)
                runCatching {
                    startActivity(
                        Intent(this, CallChoiceActivity::class.java)
                            .putExtra("number", number)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { placeCallUnmodified() }   // lukt het schermpje niet: gewoon mobiel bellen
            }
        }
    }

    /** Verco (centrale/SIP) laten bellen en het belscherm naar voren brengen. */
    internal fun callViaVerco(number: String) {
        runCatching {
            (applicationContext as VercoApp).phone.call(number)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        @Volatile private var pending: VercoCallRedirectionService? = null
        @Volatile private var pendingNumber: String = ""

        private fun arm(s: VercoCallRedirectionService, number: String) {
            pending = s; pendingNumber = number
        }

        /** Aangeroepen door CallChoiceActivity: "zaak", "prive" of "cancel". */
        fun decide(choice: String) {
            val s = pending ?: return
            val num = pendingNumber
            pending = null
            when (choice) {
                "zaak" -> { s.cancelCall(); s.callViaVerco(num) }
                "prive" -> s.placeCallUnmodified()
                else -> s.cancelCall()
            }
        }
    }
}
