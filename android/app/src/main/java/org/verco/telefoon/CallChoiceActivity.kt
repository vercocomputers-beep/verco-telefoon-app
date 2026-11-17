package org.verco.telefoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

/** Keuzeschermpje Zaak/Privé bij een uitgaand gesprek vanuit de contacten (modus "Altijd vragen"). */
class CallChoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra("number") ?: ""
        setContent {
            VercoTheme {
                AlertDialog(
                    onDismissRequest = { decide("cancel") },
                    title = { Text("Bellen naar $number") },
                    text = { Text("Waarmee wil je bellen?") },
                    confirmButton = { TextButton(onClick = { decide("zaak") }) { Text("Zaak (Verco)") } },
                    dismissButton = { TextButton(onClick = { decide("prive") }) { Text("Privé (mobiel)") } },
                )
            }
        }
    }

    private fun decide(choice: String) {
        runCatching { VercoCallRedirectionService.decide(choice) }
        finish()
    }
}
