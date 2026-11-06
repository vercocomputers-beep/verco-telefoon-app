package org.verco.telefoon

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        perms.launch(wanted.toTypedArray())

        handleLink(intent)
        // instellingen gewijzigd -> bellen (opnieuw) starten, service aan/uit
        lifecycleScope.launch {
            app.store.config.collectLatest { cfg ->
                if (cfg != null) {
                    app.phone.start(cfg)
                    PhoneService.start(this@MainActivity)
                } else {
                    app.phone.stop()
                    PhoneService.stop(this@MainActivity)
                }
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
        val data = intent?.dataString ?: return
        if (data.startsWith("verco-telefoon:")) lifecycleScope.launch { app.store.setup(data) }
    }
}
