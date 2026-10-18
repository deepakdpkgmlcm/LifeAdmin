package com.lifeadmin.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifeadmin.app.BuildConfig
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.util.Format
import com.lifeadmin.app.reminders.Notifier
import android.net.Uri
import android.os.Build
import com.lifeadmin.app.ui.theme.Extra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lifeadmin.app.ui.theme.Accent
import com.lifeadmin.app.ui.theme.ThemeState
import com.lifeadmin.app.util.Money

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val extra = Extra.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var hour by remember { mutableIntStateOf(vm.settings.reminderHour) }
    var currency by remember { mutableStateOf(vm.settings.currency) }
    var notificationsOn by remember { mutableStateOf(Notifier.canNotify(context)) }
    var testSent by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    var exactOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    var fullScreenOk by remember { mutableStateOf(Notifier.canUseFullScreen(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = Notifier.canNotify(context)
        val wasExact = exactOk
        exactOk = AlarmScheduler.canScheduleExact(context)
        fullScreenOk = Notifier.canUseFullScreen(context)
        if (exactOk && !wasExact) vm.rescheduleAll()
    }

    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = vm.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the file")
                }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
            }.getOrNull()
            if (text == null) snackbar.showSnackbar("Couldn't read that file") else pendingImport = text
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            PageHeader(eyebrow = "LifeAdmin ${BuildConfig.VERSION_NAME}", title = "Settings")

            LabeledCard("Reminders") {
                if (!notificationsOn) {
                    Text(
                        "Notifications are turned off, so reminders can't reach you.",
                        color = extra.overdue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = extra.ink, contentColor = extra.onInk)
                    ) { Text("Turn on notifications", fontWeight = FontWeight.Bold) }
                }
                PermissionRow(
                    title = "Exact timing",
                    ok = exactOk,
                    okText = "On: reminders arrive on the minute",
                    offText = "Off: alerts may be a few minutes late"
                ) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                PermissionRow(
                    title = "Full-screen alarms",
                    ok = fullScreenOk,
                    okText = "On: alarms can ring over the lock screen",
                    offText = "Off: alarms show as a loud notification"
                ) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                SelectField(
                    label = "Time for all-day reminders",
                    selected = hour,
                    options = (6..22).toList(),
                    optionLabel = { LocalTime.of(it, 0).format(timeFormatter) },
                    onSelect = {
                        hour = it
                        vm.setReminderHour(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        Notifier.show(
                            context,
                            id = TEST_NOTIFICATION_ID,
                            title = "Test reminder",
                            text = "Reminders are working. You'll hear from LifeAdmin before things expire."
                        )
                        testSent = true
                    },
                    enabled = notificationsOn,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (testSent) "Sent. Check your notifications" else "Send a test notification")
                }
            }

            LabeledCard("Daily summary") {
                var digestOn by remember { mutableStateOf(vm.settings.digestEnabled) }
                var digestAt by remember { mutableIntStateOf(vm.settings.digestMinute) }
                ToggleRow("Morning summary", "One notification with today's reminders, bills and expiries", digestOn) {
                    digestOn = it
                    vm.settings.digestEnabled = it
                    AlarmScheduler.scheduleDigest(context)
                }
                if (digestOn) {
                    SelectField(
                        label = "Summary time",
                        selected = digestAt,
                        options = HALF_HOURS,
                        optionLabel = { com.lifeadmin.app.util.Format.time(it) },
                        onSelect = {
                            digestAt = it
                            vm.settings.digestMinute = it
                            AlarmScheduler.scheduleDigest(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            LabeledCard("Quiet hours") {
                var quietOn by remember { mutableStateOf(vm.settings.quietEnabled) }
                var start by remember { mutableIntStateOf(vm.settings.quietStart) }
                var end by remember { mutableIntStateOf(vm.settings.quietEnd) }
                var allowAlarms by remember { mutableStateOf(vm.settings.quietAllowAlarms) }
                ToggleRow("Quiet hours", "Reminders wait until quiet hours end", quietOn) {
                    quietOn = it
                    vm.settings.quietEnabled = it
                }
                if (quietOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField("From", start, HALF_HOURS, { com.lifeadmin.app.util.Format.time(it) }, {
                            start = it
                            vm.settings.quietStart = it
                        }, Modifier.weight(1f))
                        SelectField("Until", end, HALF_HOURS, { com.lifeadmin.app.util.Format.time(it) }, {
                            end = it
                            vm.settings.quietEnd = it
                        }, Modifier.weight(1f))
                    }
                    ToggleRow("Let alarms ring", "Alarm-style reminders still go off", allowAlarms) {
                        allowAlarms = it
                        vm.settings.quietAllowAlarms = it
                    }
                }
            }

            LabeledCard("Defaults for new items") {
                Text(
                    "Each type starts with these alerts and style. You can still change them per item.",
                    color = extra.muted,
                    fontSize = 13.sp
                )
                Kind.entries.forEach { k -> KindDefaults(vm, k) }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sounds per type (Reminders, Payments, Expiry, Alarms)") }
            }

            LabeledCard("Money") {
                var askAmount by remember { mutableStateOf(vm.settings.askActualAmount) }
                ToggleRow("Ask the actual amount", "When marking a payment as paid (bills often vary)", askAmount) {
                    askAmount = it
                    vm.settings.askActualAmount = it
                }
                OutlinedTextField(
                    value = currency,
                    onValueChange = { new ->
                        if (new.length <= 3 && new.all { it.isLetter() }) {
                            currency = new.uppercase(Locale.ROOT)
                            if (currency.length == 3) vm.settings.currency = currency
                        }
                    },
                    label = { Text("Default currency (e.g. USD, EUR, INR)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Totals on the Timeline, Insights and Done use this currency.",
                    color = extra.muted,
                    fontSize = 13.sp
                )
                ExchangeRates(vm, currency)
            }

            LabeledCard("Appearance") {
                Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                ChoicePills(listOf("SYSTEM", "LIGHT", "DARK"), ThemeState.mode, {
                    when (it) { "LIGHT" -> "Light"; "DARK" -> "Dark"; else -> "System" }
                }) {
                    ThemeState.mode = it
                    vm.settings.themeMode = it
                }
                Text("Accent colour", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { a ->
                        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val selected = ThemeState.accent == a
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (dark) a.dark else a.light)
                                .border(if (selected) 3.dp else 0.dp, if (selected) extra.ink else Color.Transparent, CircleShape)
                                .clickable(role = Role.RadioButton, onClickLabel = a.label) {
                                    ThemeState.accent = a
                                    vm.settings.accent = a.name
                                }
                                .semantics { contentDescription = a.label + if (selected) ", selected" else "" },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            LabeledCard("Privacy") {
                val lockAvailable = remember { AppLock.available(context) }
                var lockOn by remember { mutableStateOf(vm.settings.appLock && lockAvailable) }
                ToggleRow(
                    "App lock",
                    if (lockAvailable) "Ask for fingerprint, face or screen lock when opening the app"
                    else "Set up a screen lock or fingerprint in your phone's settings to use this",
                    lockOn
                ) { on ->
                    if (!lockAvailable) return@ToggleRow
                    val activity = context as? androidx.fragment.app.FragmentActivity ?: return@ToggleRow
                    // Confirm it's really them before turning the lock on or off.
                    AppLock.prompt(activity) {
                        lockOn = on
                        vm.settings.appLock = on
                    }
                }
            }

            LabeledCard("Backup") {
                Text(
                    "Save everything to a file (Google Drive, Downloads, email) and restore it on any phone.",
                    color = extra.muted,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("lifeadmin-backup-${LocalDate.now()}.json") },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Restore")
                    }
                }
            }

            LabeledCard("About") {
                Text(
                    "Your data is stored only on this phone. No account, no bank access, no SMS reading.",
                    fontSize = 14.sp
                )
                InfoRow("Version", BuildConfig.VERSION_NAME)
                Text(
                    "Fonts: Plus Jakarta Sans and DM Mono, under the SIL Open Font License.",
                    color = extra.muted,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces everything currently in the app with the backup's contents.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val message = try {
                            val count = vm.importBackup(text)
                            if (count == 1) "Restored 1 item" else "Restored $count items"
                        } catch (e: IllegalArgumentException) {
                            e.message ?: "That file isn't a LifeAdmin backup"
                        } catch (e: Exception) {
                            "Couldn't restore that backup"
                        }
                        snackbar.showSnackbar(message)
                    }
                }) { Text("Restore", color = extra.overdue) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
}

private const val TEST_NOTIFICATION_ID = 999_999
private val HALF_HOURS = (0 until 48).map { it * 30 }

/** Rates for other currencies, so their amounts count in the totals. */
@Composable
private fun ExchangeRates(vm: AppViewModel, main: String) {
    val extra = Extra.colors
    var rates by remember { mutableStateOf(Money.parseRates(vm.settings.exchangeRates)) }
    var code by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    fun save(new: Map<String, Double>) {
        rates = new
        vm.settings.exchangeRates = Money.ratesToString(new)
    }
    Text("Exchange rates", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Text(
        "Items in other currencies are added to totals using these rates. Update them now and then.",
        color = extra.muted, fontSize = 12.sp
    )
    rates.entries.sortedBy { it.key }.forEach { (c, r) ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("1 $c = ${Format.money(r, main)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { save(rates - c) }) { Text("Remove") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = code,
            onValueChange = { new -> if (new.length <= 3 && new.all { it.isLetter() }) code = new.uppercase(Locale.ROOT) },
            label = { Text("Currency") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = rate,
            onValueChange = { new -> if (new.all { it.isDigit() || it == '.' }) rate = new },
            label = { Text("= $main") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        val value = rate.toDoubleOrNull()
        TextButton(
            enabled = code.length == 3 && !code.equals(main, true) && value != null && value > 0,
            onClick = {
                save(rates + (code to value!!))
                code = ""
                rate = ""
            }
        ) { Text("Add") }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = Extra.colors.muted, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val DEFAULT_TIMED = listOf(0, 5, 10, 15, 30, 60, 1440)
private val DEFAULT_ALL_DAY = listOf(0, 1440, 4320, 10080, 20160, 43200, 129600)

@Composable
private fun KindDefaults(vm: AppViewModel, kind: Kind) {
    val extra = Extra.colors
    var alerts by remember { mutableStateOf(vm.settings.defaultAlerts(kind)) }
    var style by remember { mutableStateOf(vm.settings.defaultStyle(kind)) }
    val selected = if (alerts == Item.NO_ALERTS) emptySet() else alerts.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val timed = kind == Kind.REMINDER
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (kind) {
                Kind.REMINDER -> "Reminders"
                Kind.PAYMENT -> "Payments"
                Kind.RENEWAL -> "Expiry & renewals"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = ((if (timed) DEFAULT_TIMED else DEFAULT_ALL_DAY) + selected).distinct().sorted()
            items(options.size) { i ->
                val m = options[i]
                androidx.compose.material3.FilterChip(
                    selected = m in selected,
                    onClick = {
                        val updated = if (m in selected) selected - m else selected + m
                        alerts = if (updated.isEmpty()) Item.NO_ALERTS else updated.sortedDescending().joinToString(",")
                        vm.settings.setDefaultAlerts(kind, alerts)
                    },
                    label = { Text(if (m == 0 && !timed) "On the day" else Format.offsetText(m)) }
                )
            }
        }
        ChoicePills(AlertStyle.entries.toList(), style, { it.label }) {
            style = it
            vm.settings.setDefaultStyle(kind, it)
        }
        androidx.compose.material3.HorizontalDivider(color = extra.line)
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, okText: String, offText: String, onFix: () -> Unit) {
    val extra = Extra.colors
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(if (ok) okText else offText, color = if (ok) extra.muted else extra.soon, fontSize = 12.sp)
        }
        if (!ok) TextButton(onClick = onFix) { Text("Allow", fontWeight = FontWeight.Bold) }
    }
}
