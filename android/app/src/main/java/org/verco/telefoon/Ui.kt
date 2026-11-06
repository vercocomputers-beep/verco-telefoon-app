package org.verco.telefoon

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Navy = Color(0xFF12355B)
val Accent = Color(0xFF1B4F8A)
val Green = Color(0xFF1F883D)
val Red = Color(0xFFCF222E)

@Composable
fun VercoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, secondary = Navy), content = content)
}

@Composable
fun AppRoot(app: VercoApp, onScan: () -> Unit) {
    val cfg by app.store.config.collectAsState()
    val phase by app.phone.phase.collectAsState()
    when {
        cfg == null -> SetupScreen(app, onScan)
        phase != PhoneManager.Phase.NONE -> CallScreen(app)
        else -> MainScreen(app)
    }
}

// ------------------------------------------------------------------ instellen

@Composable
fun SetupScreen(app: VercoApp, onScan: () -> Unit) {
    val busy by app.store.busy.collectAsState()
    val error by app.store.error.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Navy), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Verco Telefoon", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Scan de QR-code die je van je beheerder krijgt.\nDaarna bel je met je eigen toestelnummer.",
            color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        if (busy) CircularProgressIndicator() else {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("QR-code scannen")
            }
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                scope.launch { app.store.setup(text) }
            }) { Text("Instellink plakken") }
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Red) }
        Spacer(Modifier.height(24.dp))
        Text("Zorg dat je op de wifi van kantoor zit.", color = Color.Gray, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ hoofdscherm met tabbladen

@Composable
fun MainScreen(app: VercoApp) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val missed by app.recents.unseenMissed.collectAsState()
    val vm by app.phone.voicemailNew.collectAsState()
    Scaffold(bottomBar = {
        NavigationBar {
            val items = listOf(
                Triple("Kiezen", Icons.Filled.Dialpad, 0), Triple("Recent", Icons.Filled.History, missed),
                Triple("Contacten", Icons.Filled.People, 0), Triple("Functies", Icons.Filled.Star, vm),
                Triple("Instellingen", Icons.Filled.Settings, 0))
            items.forEachIndexed { i, (label, icon, badge) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i; if (i == 1) app.recents.unseenMissed.value = 0 },
                    icon = { BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(icon, null) } },
                    label = { Text(label, fontSize = 11.sp) })
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            StatusHeader(app)
            when (tab) {
                0 -> DialerTab(app)
                1 -> RecentTab(app)
                2 -> ContactsTab(app)
                3 -> FunctionsTab(app)
                else -> SettingsTab(app)
            }
        }
    }
}

@Composable
fun StatusHeader(app: VercoApp) {
    val cfg by app.store.config.collectAsState()
    val st by app.phone.status.collectAsState()
    val text by app.phone.statusText.collectAsState()
    val color = when (st) {
        PhoneManager.Status.REGISTERED -> Green
        PhoneManager.Status.CONNECTING -> Color(0xFFFF9800)
        PhoneManager.Status.FAILED -> Red
        else -> Color.Gray
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("${cfg?.userName ?: ""} · ${cfg?.ext ?: ""}", fontWeight = FontWeight.SemiBold)
            Text(text, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
    }
    HorizontalDivider()
}

@Composable
fun DialerTab(app: VercoApp) {
    var number by rememberSaveable { mutableStateOf("") }
    val st by app.phone.status.collectAsState()
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(number.ifEmpty { " " }, fontSize = 36.sp, maxLines = 1)
        Spacer(Modifier.height(24.dp))
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { k ->
                    Box(Modifier.padding(6.dp).size(72.dp).clip(CircleShape).background(Color(0xFFF0F2F5))
                        .clickable { number += k }, contentAlignment = Alignment.Center) { Text(k, fontSize = 28.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.size(72.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(if (number.isNotEmpty() && st == PhoneManager.Status.REGISTERED) Green else Color.LightGray)
                .clickable(enabled = number.isNotEmpty() && st == PhoneManager.Status.REGISTERED) { app.phone.call(number) },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Box(Modifier.size(72.dp).clickable(enabled = number.isNotEmpty()) { number = number.dropLast(1) },
                contentAlignment = Alignment.Center) { if (number.isNotEmpty()) Icon(Icons.Filled.Backspace, null, tint = Color.Gray) }
        }
    }
}

@Composable
fun RecentTab(app: VercoApp) {
    val calls by app.recents.calls.collectAsState()
    val cfg by app.store.config.collectAsState()
    var onlyMissed by rememberSaveable { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("nl")) }
    Column {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                FilterChip(!onlyMissed, { onlyMissed = false }, { Text("Alle") })
                Spacer(Modifier.width(8.dp))
                FilterChip(onlyMissed, { onlyMissed = true }, { Text("Gemist") })
            }
            TextButton(onClick = { app.recents.clear() }, enabled = calls.isNotEmpty()) { Text("Wissen") }
        }
        val list = if (onlyMissed) calls.filter { it.kind == "missed" } else calls
        if (list.isEmpty()) Text("Nog geen gesprekken", Modifier.padding(24.dp), color = Color.Gray)
        LazyColumn {
            items(list) { c ->
                val name = cfg?.colleagues?.firstOrNull { it.ext == c.number }?.name?.ifEmpty { null } ?: c.name.ifEmpty { c.number }
                ListItem(
                    modifier = Modifier.clickable { app.phone.call(c.number) },
                    leadingContent = {
                        Icon(when (c.kind) { "incoming" -> Icons.Filled.CallReceived; "outgoing" -> Icons.Filled.CallMade; else -> Icons.Filled.CallMissed },
                            null, tint = if (c.kind == "missed") Red else Color.Gray)
                    },
                    headlineContent = { Text(name, color = if (c.kind == "missed") Red else Color.Unspecified) },
                    supportingContent = {
                        val what = mapOf("incoming" to "inkomend", "outgoing" to "uitgaand", "missed" to "gemist")[c.kind]
                        val dur = if (c.duration > 0) " · %d:%02d".format(c.duration / 60, c.duration % 60) else ""
                        Text("${c.number} · $what$dur")
                    },
                    trailingContent = { Text(fmt.format(Date(c.time)), fontSize = 12.sp, color = Color.Gray) },
                )
            }
        }
    }
}

@Composable
fun PresenceDot(p: PhoneManager.Presence) {
    val color = when (p) {
        PhoneManager.Presence.AVAILABLE -> Green
        PhoneManager.Presence.BUSY, PhoneManager.Presence.DND -> Red
        PhoneManager.Presence.OFFLINE -> Color(0x80888888)
        else -> Color(0x40888888)
    }
    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
}

fun presenceLabel(p: PhoneManager.Presence) = when (p) {
    PhoneManager.Presence.AVAILABLE -> "vrij"
    PhoneManager.Presence.BUSY -> "in gesprek"
    PhoneManager.Presence.DND -> "niet storen"
    PhoneManager.Presence.OFFLINE -> "niet bereikbaar"
    else -> "onbekend"
}

@Composable
fun ContactsTab(app: VercoApp) {
    val cfg by app.store.config.collectAsState()
    val presence by app.phone.presence.collectAsState()
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(cfg?.phonebook) { contacts = Phonebook.load(cfg?.phonebook ?: "") }
    val exts = cfg?.colleagues?.map { it.ext }?.toSet() ?: emptySet()
    val cols = (cfg?.colleagues ?: emptyList()).filter { q.isEmpty() || it.name.contains(q, true) || it.ext.contains(q) }
    val others = contacts.filter { c -> c.numbers.none { it in exts } }
        .filter { q.isEmpty() || it.name.contains(q, true) || it.numbers.any { n -> n.contains(q) } }
    Column {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(8.dp), placeholder = { Text("Zoek naam of nummer") },
            singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) })
        LazyColumn {
            if (cols.isNotEmpty()) item { SectionTitle("Collega's") }
            items(cols) { c ->
                val p = presence[c.ext] ?: PhoneManager.Presence.UNKNOWN
                ListItem(modifier = Modifier.clickable { app.phone.call(c.ext) },
                    leadingContent = { PresenceDot(p) },
                    headlineContent = { Text(c.name.ifEmpty { c.ext }) },
                    supportingContent = { Text("${c.ext} · ${presenceLabel(p)}") },
                    trailingContent = { Icon(Icons.Filled.Call, null, tint = Green) })
            }
            if (others.isNotEmpty()) item { SectionTitle("Contacten") }
            items(others) { c ->
                ListItem(modifier = Modifier.clickable { c.numbers.firstOrNull()?.let { app.phone.call(it) } },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, null, tint = Accent) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.numbers.joinToString(" · ")) },
                    trailingContent = { Icon(Icons.Filled.Call, null, tint = Green) })
            }
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

@Composable
fun FunctionsTab(app: VercoApp) {
    val cfg by app.store.config.collectAsState()
    val vm by app.phone.voicemailNew.collectAsState()
    LazyColumn {
        if (!cfg?.voicemail.isNullOrEmpty()) item {
            ListItem(modifier = Modifier.clickable { app.phone.call(cfg!!.voicemail) },
                leadingContent = { Icon(Icons.Filled.Voicemail, null, tint = Accent) },
                headlineContent = { Text("Voicemail beluisteren") },
                trailingContent = { if (vm > 0) Badge(containerColor = Red) { Text("$vm nieuw", color = Color.White) } })
            HorizontalDivider()
        }
        item { SectionTitle("Functies") }
        items(cfg?.functions ?: emptyList()) { f ->
            ListItem(modifier = Modifier.clickable { app.phone.call(f.code) },
                leadingContent = { Icon(Icons.Filled.Star, null, tint = Accent) },
                headlineContent = { Text(f.name) },
                trailingContent = { Text(f.code, color = Color.Gray) })
        }
    }
}

@Composable
fun SettingsTab(app: VercoApp) {
    val cfg by app.store.config.collectAsState()
    val text by app.phone.statusText.collectAsState()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val c = cfg ?: return
    LazyColumn {
        item { SectionTitle("Account") }
        item { KV("Naam", c.userName) }
        item { KV("Toestelnummer", c.ext) }
        item { KV("Bedrijf", c.company) }
        item { KV("Status", text) }
        item { SectionTitle("Centrale") }
        item { KV("Adres", c.server) }
        item { KV("Gebruiker", c.sipUser) }
        item {
            TextButton(onClick = { scope.launch { app.store.refresh() } }) { Text("Instellingen verversen bij de centrale") }
            TextButton(onClick = { app.phone.stop(); app.phone.start(c) }) { Text("Opnieuw verbinden") }
            TextButton(onClick = { confirm = true }) { Text("Afmelden van deze telefoon", color = Red) }
        }
        item { Text("Verco Telefoon 0.1 (Android) · open source (GPLv3) · Linphone-bouwdoos van Belledonne Communications",
            Modifier.padding(16.dp), fontSize = 12.sp, color = Color.Gray) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Afmelden?") }, text = { Text("Je moet daarna opnieuw de QR-code scannen.") },
        confirmButton = { TextButton(onClick = { confirm = false; app.store.logout() }) { Text("Afmelden", color = Red) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuleren") } })
}

@Composable
fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k); Text(v, color = Color.Gray, maxLines = 1)
    }
}

// ------------------------------------------------------------------ gesprek

@Composable
fun CallScreen(app: VercoApp) {
    val phase by app.phone.phase.collectAsState()
    val name by app.phone.remoteName.collectAsState()
    val number by app.phone.remoteNumber.collectAsState()
    val since by app.phone.connectedAt.collectAsState()
    val muted by app.phone.muted.collectAsState()
    val speaker by app.phone.speaker.collectAsState()
    var keypad by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) { while (phase == PhoneManager.Phase.ACTIVE) { now = System.currentTimeMillis(); delay(1000) } }
    Column(Modifier.fillMaxSize().background(Navy).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(name.ifEmpty { number }, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (name.isNotEmpty()) Text(number, color = Color(0xB3FFFFFF))
        Spacer(Modifier.height(8.dp))
        val stateText = when (phase) {
            PhoneManager.Phase.INCOMING -> "Belt je…"
            PhoneManager.Phase.OUTGOING -> "Bellen…"
            PhoneManager.Phase.RINGING -> "Gaat over…"
            PhoneManager.Phase.ACTIVE -> if (since > 0) ((now - since) / 1000).let { "%d:%02d".format(it / 60, it % 60) } else "Verbonden"
            PhoneManager.Phase.ENDED -> "Gesprek beëindigd"
            else -> ""
        }
        Text(stateText, color = Color(0xCCFFFFFF))
        Spacer(Modifier.weight(1f))
        if (phase == PhoneManager.Phase.ACTIVE) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RoundButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Stil", muted) { app.phone.toggleMute() }
                RoundButton(Icons.Filled.Dialpad, "Toetsen", keypad) { keypad = !keypad }
                RoundButton(Icons.Filled.VolumeUp, "Luidspreker", speaker) { app.phone.toggleSpeaker() }
                RoundButton(Icons.Filled.PhoneForwarded, "Doorverbinden", false) { transfer = true }
            }
            if (keypad) {
                Spacer(Modifier.height(12.dp))
                listOf("123", "456", "789", "*0#").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { ch ->
                            Box(Modifier.padding(4.dp).size(54.dp).clip(CircleShape).background(Color(0x26FFFFFF)).clickable { app.phone.dtmf(ch) },
                                contentAlignment = Alignment.Center) { Text("$ch", color = Color.White, fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            if (phase == PhoneManager.Phase.INCOMING) BigButton(Icons.Filled.Call, Green) { app.phone.accept() }
            if (phase != PhoneManager.Phase.ENDED) BigButton(Icons.Filled.CallEnd, Red) { app.phone.hangup() }
        }
        Spacer(Modifier.height(40.dp))
    }
    if (transfer) TransferDialog(app) { transfer = false }
}

@Composable
fun RoundButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(if (on) Color.White else Color(0x2EFFFFFF)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (on) Navy else Color.White) }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun BigButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun TransferDialog(app: VercoApp, onClose: () -> Unit) {
    val cfg by app.store.config.collectAsState()
    val presence by app.phone.presence.collectAsState()
    var number by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val go = { n: String -> if (app.phone.transfer(n)) onClose() else failed = true }
    AlertDialog(onDismissRequest = onClose, title = { Text("Doorverbinden") },
        text = {
            Column {
                OutlinedTextField(number, { number = it }, label = { Text("Nummer") }, singleLine = true)
                if (failed) Text("Doorverbinden is niet gelukt.", color = Red)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(cfg?.colleagues ?: emptyList()) { c ->
                        Row(Modifier.fillMaxWidth().clickable { go(c.ext) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PresenceDot(presence[c.ext] ?: PhoneManager.Presence.UNKNOWN)
                            Spacer(Modifier.width(10.dp))
                            Text(c.name.ifEmpty { c.ext }, Modifier.weight(1f))
                            Text(c.ext, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { go(number) }, enabled = number.isNotEmpty()) { Text("Doorverbinden") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuleren") } })
}
