package org.verco.telefoon

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Android-push via Firebase: de centrale wekt de app als er gebeld wordt, ook als Android de app heeft gesloten.
 * De Firebase-gegevens komen van de centrale (/prov/vt → push.fcm), niet uit de app: zo staat er niets in de
 * openbare broncode en werkt dezelfde app bij elke centrale die met hetzelfde Firebase-project is ingesteld.
 */
object Push {
    private const val TAG = "VercoPush"

    fun init(context: Context, cfg: ProvisioningConfig) {
        val f = cfg.fcm ?: return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(f.projectId)
                    .setApplicationId(f.appId)
                    .setApiKey(f.apiKey)
                    .setGcmSenderId(f.senderId)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> tokenChanged(token, f.projectId) }
                .addOnFailureListener { Log.w(TAG, "Geen pushsleutel van Firebase: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase starten mislukt: ${e.message}")
        }
    }

    fun tokenChanged(token: String, project: String? = null) {
        val app = VercoApp.instance
        val p = project ?: app.store.config.value?.fcm?.projectId ?: return
        Handler(Looper.getMainLooper()).post { app.phone.setPushToken(token, p) }
    }
}

/** Ontvangt de pushberichten van Firebase. */
class VercoFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.tokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "call") return
        val app = VercoApp.instance
        val cfg = app.store.config.value ?: return
        Handler(Looper.getMainLooper()).post {
            PhoneService.start(app)
            app.phone.wake(cfg)
        }
    }
}
