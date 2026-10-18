package com.lifeadmin.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.reminders.AlarmReceiver
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.reminders.Schedule
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.LifeAdminTheme
import com.lifeadmin.app.util.Format
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Full-screen ringing alarm, shown over the lock screen for reminders set to "Alarm". */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
        val occurrence = intent.getLongExtra(AlarmReceiver.EXTRA_OCCURRENCE, 0L)

        setContent {
            LifeAdminTheme {
                AlarmScreen(
                    itemId = id,
                    onDone = {
                        sendBroadcast(AlarmReceiver.actionBroadcast(this, AlarmReceiver.ACTION_DONE, id, occurrence, 0))
                        finish()
                    },
                    onSnooze = { minutes ->
                        sendBroadcast(AlarmReceiver.actionBroadcast(this, AlarmReceiver.ACTION_SNOOZE, id, occurrence, minutes))
                        finish()
                    },
                    onDismiss = {
                        Notifier.cancel(this, id.toInt())
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        fun intent(context: Context, itemId: Long, occurrence: Long): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(AlarmReceiver.EXTRA_ID, itemId)
                putExtra(AlarmReceiver.EXTRA_OCCURRENCE, occurrence)
            }
    }
}

private val AlarmBg = Color(0xFF0B1116)
private val AlarmInk = Color(0xFFE6EDF3)
private val AlarmMuted = Color(0xFF94A3B8)
private val AlarmAccent = Color(0xFF5EEAD4)

@Composable
private fun AlarmScreen(
    itemId: Long,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val item by produceState<Item?>(initialValue = null, itemId) {
        value = AppDatabase.get(context).itemDao().get(itemId)
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    BackHandler(onBack = onDismiss)

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmBg)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            Format.time(now),
            fontFamily = DmMono,
            fontSize = 64.sp,
            color = AlarmInk
        )
        Text(
            Format.headerDate(now.toLocalDate()),
            fontFamily = DmMono,
            fontSize = 13.sp,
            color = AlarmMuted,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(150.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(AlarmAccent.copy(alpha = 0.15f))
            )
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AlarmAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = AlarmBg, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        val current = item
        Text(
            current?.title ?: "Reminder",
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AlarmInk,
            textAlign = TextAlign.Center
        )
        if (current != null) {
            val occ = Schedule.occurrence(current, AlarmScheduler.defaultMinute(context))
            Spacer(Modifier.height(8.dp))
            Text(
                AlarmReceiver.alertText(current, occ, now, snoozed = false),
                fontSize = 15.sp,
                color = AlarmMuted,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlarmAccent, contentColor = AlarmBg),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(current?.kind?.doneLabel ?: "Done", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        Text("Snooze", color = AlarmMuted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5, 10, 30, 60).forEach { m ->
                OutlinedButton(
                    onClick = { onSnooze(m) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlarmInk),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (m < 60) "${m}m" else "1h", fontWeight = FontWeight.Bold)
                }
            }
        }
        TextButton(onClick = onDismiss) { Text("Dismiss", color = AlarmMuted) }
    }
}
