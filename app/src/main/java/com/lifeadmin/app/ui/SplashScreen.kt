package com.lifeadmin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import kotlin.math.sqrt

/** Progress of a sub-animation that runs between fractions [start] and [end] of the whole. */
private fun phase(p: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((p - start) / (end - start)).coerceIn(0f, 1f))

/** Springy overshoot for "pop" moments. */
private fun pop(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f
    val c = 1.70158f + 1f
    val x = t - 1f
    return 1f + c * x * x * x + 1.70158f * x * x
}

/**
 * Animated launch screen in the Calm Timeline style: the app icon builds itself
 * (tile, calendar, check mark), a timeline rail with urgency dots draws underneath,
 * then the name fades in. [durationMs] is 4000 the first time and 2000 afterwards.
 */
@Composable
fun SplashScreen(durationMs: Int, onFinished: () -> Unit) {
    val extra = Extra.colors
    val primary = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(0f) }
    val finish = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        finish.value()
    }

    // The short version runs the same story, just faster; the long one lingers at the end.
    val p = progress.value
    val tileIn = pop(phase(p, 0.00f, 0.22f))
    val cardIn = phase(p, 0.12f, 0.36f)
    val check = phase(p, 0.30f, 0.52f)
    val rail = phase(p, 0.40f, 0.66f)
    val dot1 = pop(phase(p, 0.48f, 0.60f))
    val dot2 = pop(phase(p, 0.54f, 0.66f))
    val dot3 = pop(phase(p, 0.60f, 0.72f))
    val name = phase(p, 0.50f, 0.74f)
    val tagline = phase(p, 0.62f, 0.84f)
    val exit = phase(p, 0.92f, 1.00f)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - exit
                scaleX = 1f + exit * 0.06f
                scaleY = 1f + exit * 0.06f
            }
        ) {
            Canvas(Modifier.size(136.dp)) {
                drawAppIcon(tileIn, cardIn, check, primary)
            }
            Spacer(Modifier.height(22.dp))
            Canvas(
                Modifier
                    .width(150.dp)
                    .height(18.dp)
            ) {
                val y = size.height / 2
                val x0 = 8.dp.toPx()
                val x1 = size.width - 8.dp.toPx()
                drawLine(
                    extra.line, Offset(x0, y), Offset(x0 + (x1 - x0) * rail, y),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
                val r = 6.dp.toPx()
                listOf(
                    Triple(0.12f, dot1, extra.overdue),
                    Triple(0.5f, dot2, extra.soonDot),
                    Triple(0.88f, dot3, primary),
                ).forEach { (at, s, color) ->
                    if (s > 0f) drawCircle(color, radius = r * s, center = Offset(x0 + (x1 - x0) * at, y))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "LifeAdmin",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = extra.ink,
                modifier = Modifier.graphicsLayer {
                    alpha = name
                    translationY = (1f - name) * 24.dp.toPx()
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "REMINDERS · RENEWALS · PAYMENTS",
                fontFamily = DmMono,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = extra.muted,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline
                    translationY = (1f - tagline) * 12.dp.toPx()
                }
            )
        }
    }
}

/** Draws the launcher icon (on a 108-unit grid) with each part at its own progress. */
private fun DrawScope.drawAppIcon(tileIn: Float, cardIn: Float, check: Float, tile: Color) {
    val u = size.minDimension / 108f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)

    scale(tileIn.coerceAtLeast(0.001f), pivot = center) {
        drawRoundRect(tile, topLeft = Offset.Zero, size = size, cornerRadius = CornerRadius(30 * u))
    }
    if (cardIn <= 0f) return

    val cardScale = 0.6f + 0.4f * cardIn
    scale(cardScale, pivot = center) {
        val a = cardIn
        // Calendar body, header band and binder rings.
        drawRoundRect(Color.White.copy(alpha = a), topLeft = o(34f, 36f), size = Size(40 * u, 40 * u), cornerRadius = CornerRadius(4 * u))
        drawRoundRect(Color(0xFF5EEAD4).copy(alpha = a), topLeft = o(34f, 36f), size = Size(40 * u, 11 * u), cornerRadius = CornerRadius(4 * u))
        drawRect(Color.White.copy(alpha = a), topLeft = o(42f, 31f), size = Size(5 * u, 10 * u))
        drawRect(Color.White.copy(alpha = a), topLeft = o(61f, 31f), size = Size(5 * u, 10 * u))

        // Check mark drawn stroke by stroke: (44,61) -> (51,68) -> (64,54).
        if (check > 0f) {
            val seg1 = sqrt(7f * 7f + 7f * 7f)
            val seg2 = sqrt(13f * 13f + 14f * 14f)
            val drawn = (seg1 + seg2) * check
            val w = 5 * u
            val p0 = o(44f, 61f)
            val p1 = o(51f, 68f)
            val p2 = o(64f, 54f)
            if (drawn <= seg1) {
                val t = drawn / seg1
                drawLine(tile, p0, p0 + (p1 - p0) * t, strokeWidth = w, cap = StrokeCap.Round)
            } else {
                val t = (drawn - seg1) / seg2
                drawLine(tile, p0, p1, strokeWidth = w, cap = StrokeCap.Round)
                drawLine(tile, p1, p1 + (p2 - p1) * t, strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}
