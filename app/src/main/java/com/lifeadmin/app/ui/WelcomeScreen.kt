package com.lifeadmin.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import kotlinx.coroutines.launch

private data class Page(val eyebrow: String, val title: String, val body: String, val icon: ImageVector)

private val pages = listOf(
    Page(
        "01 · Remember",
        "Never miss a renewal",
        "Passports, subscriptions, bills, insurance, warranties. Put every date that matters on one calm timeline.",
        Icons.Outlined.EventAvailable
    ),
    Page(
        "02 · Private",
        "Your data stays on your phone",
        "No account, no bank login, no SMS reading. Back it up to a file whenever you like.",
        Icons.Outlined.Lock
    ),
    Page(
        "03 · Remind",
        "A nudge before it's due",
        "Choose when to be reminded: a month, a week, a day before, or on the day itself.",
        Icons.Outlined.NotificationsActive
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val extra = Extra.colors
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onFinish()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LifeAdmin", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onFinish) { Text("Skip", color = extra.muted) }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(page.eyebrow.uppercase(), fontFamily = DmMono, fontSize = 12.sp, color = extra.muted, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))
                Text(page.title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                Text(page.body, fontSize = 16.sp, lineHeight = 24.sp, color = extra.muted, textAlign = TextAlign.Start)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    val w by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotW")
                    val c by animateColorAsState(if (active) extra.ink else extra.line, label = "dotC")
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(w)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c)
                    )
                }
            }
            Button(
                onClick = {
                    if (!last) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onFinish()
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = extra.ink, contentColor = extra.onInk),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp)
            ) {
                Text(if (last) "Get started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}
