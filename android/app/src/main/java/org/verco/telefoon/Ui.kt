package org.verco.telefoon

import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
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

val Navy = Color(0xFF0E2F55)
val Accent = Color(0xFF1B6FC4)
val Teal = Color(0xFF17B3A3)
val Green = Color(0xFF1F9E4A)
val Red = Color(0xFFCF222E)
val Surface = Color(0xFFF4F7FB)

@Composable
fun VercoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent, secondary = Navy, tertiary = Teal,
            background = Surface, surface = Color.White,
        ),
        content = content,
    )
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
        Column(Modifier.padding(pad).background(Surface)) {
            BrandHeader(app)
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
fun BrandHeader(app: VercoApp) {
    val cfg by app.store.config.collectAsState()
    val st by app.phone.status.collectAsState()
    val text by app.phone.statusText.collectAsState()
    val ctx = LocalContext.current
    val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    val dot = when (st) {
        PhoneManager.Status.REGISTERED -> Green
        PhoneManager.Status.CONNECTING -> Color(0xFFFFB020)
        PhoneManager.Status.FAILED -> Red
        else -> Color(0xFFB9C2CF)
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Navy, Accent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, null, tint = Navy, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Verco Telefoon", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (ver.isNotEmpty()) Text("versie $ver", color = Color(0xB3FFFFFF), fontSize = 12.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(7.dp))
                Text(if (st == PhoneManager.Status.REGISTERED) "Verbonden" else text, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${cfg?.userName ?: ""}  ·  toestel ${cfg?.ext ?: ""}",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

@Composable
fun DialerTab(app: VercoApp) {
    var number by rememberSaveable { mutableStateOf("") }
    val st by app.phone.status.collectAsState()
    val spk by app.phone.speakerPreset.collectAsState()
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    val sub = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(number.ifEmpty { "Nummer" }, fontSize = 38.sp, maxLines = 1,
            fontWeight = FontWeight.SemiBold, color = if (number.isEmpty()) Color(0xFFB9C2CF) else Navy)
        Spacer(Modifier.height(24.dp))
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { k ->
                    Box(Modifier.padding(6.dp).size(74.dp).clip(CircleShape).background(Color.White)
                        .clickable { number += k; app.phone.playKeyTone(k[0]) }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(k, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Navy)
                            // altijd een letterregel reserveren (ook bij 1, * en #) zodat elk cijfer even hoog staat
                            Text(sub[k] ?: " ", fontSize = 9.sp, color = Color(0xFF8A97A8), letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val canCall = number.isNotEmpty() && st == PhoneManager.Status.REGISTERED
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // de drie belknoppen gecentreerd in het midden
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(66.dp).clip(CircleShape).background(if (spk) Green else Color.White)
                    .clickable { app.phone.setSpeakerPreset(!spk) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VolumeUp, "Luidspreker", tint = if (spk) Color.White else Color(0xFF8A97A8), modifier = Modifier.size(28.dp))
                }
                Box(Modifier.size(74.dp).clip(CircleShape).background(if (canCall) Green else Color(0xFFCBD5E1))
                    .clickable(enabled = canCall) { app.phone.call(number) },
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, "Bellen", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Box(Modifier.size(74.dp).clip(CircleShape).background(if (canCall) Accent else Color(0xFFCBD5E1))
                    .clickable(enabled = canCall) { app.phone.call(number, true) },
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.Videocam, "Videobellen", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
            // backspace apart rechts, zodat hij het centreren niet verstoort
            Box(Modifier.align(Alignment.CenterEnd).size(66.dp).clickable(enabled = number.isNotEmpty()) { number = number.dropLast(1) },
                contentAlignment = Alignment.Center) { if (number.isNotEmpty()) Icon(Icons.Filled.Backspace, null, tint = Color.Gray) }
        }
        if (spk) { Spacer(Modifier.height(10.dp)); Text("Luidspreker aan — het gesprek start op de speaker", color = Green, fontSize = 13.sp) }
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
    // Collega-status via de centrale (HTTP): meteen en daarna elke 10s, zodat de bolletjes snel kloppen.
    LaunchedEffect(Unit) {
        app.phone.refreshPresence()   // SIP-presence ook even aanstoten
        while (true) {
            app.store.colleaguePresence()?.let { app.phone.presence.value = app.phone.presence.value + it }
            delay(10_000)
        }
    }
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
    val dnd by app.store.dnd.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.store.refreshDnd() }
    LazyColumn {
        item {
            ListItem(
                leadingContent = { Icon(if (dnd) Icons.Filled.DoNotDisturbOn else Icons.Filled.DoNotDisturbOff,
                    null, tint = if (dnd) Red else Color.Gray) },
                headlineContent = { Text("Niet storen") },
                supportingContent = { Text(if (dnd) "Staat aan — je toestel gaat niet over" else "Staat uit") },
                trailingContent = { Switch(checked = dnd, onCheckedChange = { on -> scope.launch { app.store.setDnd(on) } }) },
            )
            HorizontalDivider()
        }
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
fun CallRoutingSetting() {
    val ctx = LocalContext.current
    if (Build.VERSION.SDK_INT < 29) {
        Text("Deze functie vereist Android 10 of nieuwer.", Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
        return
    }
    val rm = remember { ctx.getSystemService(RoleManager::class.java) }
    var held by remember { mutableStateOf(rm?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true) }
    var mode by remember { mutableStateOf(CallRouting.mode(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = rm?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (!held) {
            Text("Laat Verco kiezen tussen zakelijk en privé als je vanuit je contacten belt. Daarvoor moet je Verco eenmalig toestemming geven.",
                color = Color.Gray, fontSize = 13.sp)
            Button(onClick = {
                runCatching { rm?.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)?.let { launcher.launch(it) } }
            }, modifier = Modifier.padding(top = 10.dp)) { Text("Verco toestaan") }
        } else {
            Text("Als je vanuit je contacten belt:", color = Color.Gray, fontSize = 13.sp)
            listOf(
                CallMode.ASK to "Elke keer vragen (zaak of privé)",
                CallMode.ZAAK to "Altijd zaak (Verco)",
                CallMode.PRIVE to "Altijd privé (mobiel)",
            ).forEach { (m, label) ->
                Row(Modifier.fillMaxWidth().clickable { mode = m; CallRouting.setMode(ctx, m) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == m, onClick = { mode = m; CallRouting.setMode(ctx, m) })
                    Spacer(Modifier.width(4.dp))
                    Text(label)
                }
            }
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
        item { SectionTitle("Bellen vanuit contacten") }
        item { CallRoutingSetting() }
        item { SectionTitle("Centrale") }
        item { KV("Adres", c.server) }
        item { KV("Gebruiker", c.sipUser) }
        item {
            TextButton(onClick = { scope.launch { app.store.refresh() } }) { Text("Instellingen verversen bij de centrale") }
            TextButton(onClick = { app.phone.stop(); app.phone.start(c) }) { Text("Opnieuw verbinden") }
            TextButton(onClick = { confirm = true }) { Text("Afmelden van deze telefoon", color = Red) }
        }
        item { Text("Verco Telefoon 0.2 (Android) · open source (GPLv3) · Linphone-bouwdoos van Belledonne Communications",
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
    val videoOn by app.phone.videoActive.collectAsState()
    LaunchedEffect(phase) { while (phase == PhoneManager.Phase.ACTIVE) { now = System.currentTimeMillis(); delay(1000) } }
    Box(Modifier.fillMaxSize().background(Navy)) {
    if (videoOn) {
        AndroidView(factory = { ctx -> TextureView(ctx).also { app.phone.setVideoWindow(it) } }, modifier = Modifier.fillMaxSize())
        AndroidView(factory = { ctx -> TextureView(ctx).also { app.phone.setPreviewWindow(it) } },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 16.dp).size(108.dp, 150.dp).background(Color.Black))
        Box(Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 16.dp).size(44.dp).clip(CircleShape).background(Color(0x55000000)).clickable { app.phone.switchCamera() },
            contentAlignment = Alignment.Center) { Icon(Icons.Filled.Cameraswitch, "Camera wisselen", tint = Color.White) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Stil", muted) { app.phone.toggleMute() }
                RoundButton(Icons.Filled.Dialpad, "Toetsen", keypad) { keypad = !keypad }
                RoundButton(Icons.Filled.VolumeUp, "Luidspreker", speaker) { app.phone.toggleSpeaker() }
                RoundButton(if (videoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Video", videoOn) { app.phone.toggleVideo() }
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
