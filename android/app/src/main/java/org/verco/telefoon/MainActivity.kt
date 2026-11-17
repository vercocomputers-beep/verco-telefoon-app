package org.verco.telefoon

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as VercoApp

    private val scan = registerForActivityResult(ScanContract()) { r ->
        r.contents?.let { text -> lifecycleScope.launch { app.store.setup(text) } }
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        perms.launch(wanted.toTypedArray())

        ensureReliabilityPrompts()

        handleLink(intent)
        // instellingen gewijzigd -> bellen (opnieuw) starten, service aan/uit
        lifecycleScope.launch {
            // 'active' = direct met de centrale (kantoor-wifi/VPN) of via het tussenstation; wisselt mee met het netwerk
            app.store.active.collectLatest { cfg ->
                if (cfg != null) {
                    app.phone.start(cfg)
                    Push.init(this@MainActivity, cfg)
                    PhoneService.start(this@MainActivity)
                } else {
                    app.phone.stop()
                    PhoneService.stop(this@MainActivity)
                }
            }
        }
        // Inkomend/actief gesprek: scherm aanzetten en over het vergrendelscherm tonen,
        // zodat het inkomend-gesprek-scherm vanzelf verschijnt (ook als het toestel op slot/scherm uit staat).
        lifecycleScope.launch {
            app.phone.phase.collectLatest { p ->
                lockScreenVisible(p == PhoneManager.Phase.INCOMING || p == PhoneManager.Phase.ACTIVE)
            }
        }
        lifecycleScope.launch { app.store.refresh() }

        setContent {
            VercoTheme {
                AppRoot(
                    app = app,
                    onScan = {
                        scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan de QR-code van de telefooncentrale").setBeepEnabled(false).setOrientationLocked(true))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        // instellink uit de QR-code: verco-telefoon://setup?...
        val data = intent?.dataString ?: return
        if (data.startsWith("verco-telefoon:")) lifecycleScope.launch { app.store.setup(data) }
    }

    /** Toont het gesprek over het vergrendelscherm en zet het scherm aan bij een inkomend/actief gesprek. */
    private fun lockScreenVisible(on: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(on)
                setTurnScreenOn(on)
            } else {
                @Suppress("DEPRECATION")
                val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                if (on) window.addFlags(flags) else window.clearFlags(flags)
            }
        }
    }

    /**
     * Zet eenmalig de rechten goed die een belapp op de achtergrond nodig heeft:
     * 1) accu-optimalisatie uit (anders zet Samsung de app in slaapstand en komt de oproep niet binnen),
     * 2) volledig-scherm-melding aan (Android 14+: anders opent het inkomend-gesprek-scherm niet vanzelf).
     * Niet stapelen: eerst de accu-vraag; de volledig-scherm-vraag volgt bij een volgende start.
     */
    private fun ensureReliabilityPrompts() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        val batteryOk = runCatching { pm?.isIgnoringBatteryOptimizations(packageName) ?: true }.getOrDefault(true)
        if (!batteryOk) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && !nm.canUseFullScreenIntent()) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }
            }
        }
    }
}
