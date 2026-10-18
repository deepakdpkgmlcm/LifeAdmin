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
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.ui.theme.Extra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = Notifier.canNotify(context)
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
                SelectField(
                    label = "Daily reminder time",
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

            LabeledCard("Money") {
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
                    "Totals on the Timeline and Insights use this currency.",
                    color = extra.muted,
                    fontSize = 13.sp
                )
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
