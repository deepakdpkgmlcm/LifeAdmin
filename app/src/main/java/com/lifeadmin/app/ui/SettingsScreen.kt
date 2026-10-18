package com.lifeadmin.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifeadmin.app.BuildConfig
import com.lifeadmin.app.reminders.Notifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var hour by remember { mutableIntStateOf(vm.settings.reminderHour) }
    var currency by remember { mutableStateOf(vm.settings.currency) }
    var notificationsOn by remember { mutableStateOf(Notifier.canNotify(context)) }
    var testSent by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = Notifier.canNotify(context)
    }

    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reminders", style = MaterialTheme.typography.titleMedium)

            if (!notificationsOn) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Notifications are turned off, so reminders can't reach you.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }) { Text("Turn on notifications") }
                    }
                }
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (testSent) "Test sent - check your notifications" else "Send a test notification")
            }

            HorizontalDivider()

            Text("Money", style = MaterialTheme.typography.titleMedium)
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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your data is stored only on this phone. No account, no bank access, no SMS reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val TEST_NOTIFICATION_ID = 999_999
