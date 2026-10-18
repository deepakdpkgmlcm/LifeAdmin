package com.lifeadmin.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.QuickParse
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.LocalDateTime

/** Turns a quick-add result into an editor draft. */
fun QuickParse.Result.toDraft(settings: AppSettings): Draft = Draft(
    title = title,
    kind = kind,
    category = category,
    dueEpochDay = date.toEpochDay(),
    dueMinute = minute,
    cycle = cycle,
    repeatEvery = every,
    repeatDays = days,
    repeatLimit = null,
    repeatUntilEpochDay = null,
    autoRenew = kind == Kind.REMINDER,
    amount = amount,
    currency = currency ?: settings.currency,
    notes = "",
    alertOffsets = settings.defaultAlerts(kind),
    alertStyle = if (alarm) AlertStyle.ALARM else settings.defaultStyle(kind),
    nagEveryMin = 0,
    nagCount = 0,
)

/** "Reminder · Mon, 19 Oct 10:30 · Every weekday · Alarm" */
private fun preview(r: QuickParse.Result): String {
    val temp = Item(
        title = r.title, category = r.category, dueEpochDay = r.date.toEpochDay(),
        cycle = r.cycle, repeatEvery = r.every, repeatDays = r.days, kind = r.kind
    )
    val parts = mutableListOf(r.kind.label, Format.shortDate(r.date) + (r.minute?.let { " " + Format.time(it) } ?: ""))
    if (r.cycle != Cycle.NONE) parts += Format.repeatText(temp)
    r.amount?.let { parts += Format.money(it, r.currency ?: "") }
    if (r.alarm) parts += "Alarm"
    return parts.joinToString(" · ")
}

/**
 * Type or say a reminder in plain words. Shows what it understood before saving;
 * "Edit details" opens the full editor pre-filled.
 */
@Composable
fun QuickAddBar(vm: AppViewModel, onEditDetails: () -> Unit, modifier: Modifier = Modifier) {
    val extra = Extra.colors
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { if (text.isBlank()) null else QuickParse.parse(text, LocalDateTime.now()) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = it }
        }
    }

    fun save() {
        val r = parsed ?: return
        vm.quickAdd(r)
        text = ""
        focus.clearFocus()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add anything… “Pay rent 5th every month 10am”", maxLines = 1) },
            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = extra.later) },
            trailingIcon = {
                if (text.isBlank()) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a reminder, e.g. “Call mom Sunday 7pm”")
                        try {
                            voice.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            vm.undoEvent.value = UndoEvent("Voice input isn't available on this phone", null)
                        }
                    }) { Icon(Icons.Outlined.Mic, contentDescription = "Speak") }
                } else {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add", tint = extra.later)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = extra.card,
                unfocusedContainerColor = extra.card,
                unfocusedBorderColor = extra.line
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(visible = parsed != null) {
            val r = parsed
            if (r != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        Text(preview(r), color = extra.muted, fontSize = 12.sp, maxLines = 2)
                    }
                    TextButton(onClick = {
                        vm.prefill.value = r.toDraft(vm.settings)
                        text = ""
                        focus.clearFocus()
                        onEditDetails()
                    }) { Text("Edit details") }
                }
            }
        }
    }
}

/** Asks how much was actually paid (bills often vary), pre-filled with the usual amount. */
@Composable
fun AmountPromptDialog(item: Item, onConfirm: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember(item.id) {
        mutableStateOf(item.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark \"${item.title}\" as paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How much did you pay?", color = Extra.colors.muted)
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> if (new.all { it.isDigit() || it == '.' || it == ',' }) text = new },
                    label = { Text("Amount" + if (item.currency.isNotBlank()) " (${item.currency})" else "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.replace(",", ".").toDoubleOrNull()) }) {
                Text("Mark as paid", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
