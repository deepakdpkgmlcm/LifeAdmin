package com.lifeadmin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifeadmin.app.ui.AppBottomBar
import com.lifeadmin.app.ui.AppViewModel
import com.lifeadmin.app.ui.CalendarScreen
import com.lifeadmin.app.ui.DetailScreen
import com.lifeadmin.app.ui.DoneScreen
import com.lifeadmin.app.ui.EditScreen
import com.lifeadmin.app.ui.HomeScreen
import com.lifeadmin.app.ui.InsightsScreen
import com.lifeadmin.app.ui.ItemsScreen
import com.lifeadmin.app.ui.SettingsScreen
import com.lifeadmin.app.ui.SplashScreen
import com.lifeadmin.app.ui.Tab
import com.lifeadmin.app.ui.WelcomeScreen
import com.lifeadmin.app.ui.theme.LifeAdminTheme
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.ui.AmountPromptDialog
import com.lifeadmin.app.ui.Draft
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    /** An app-icon shortcut waiting to be handled ("add reminder", "calendar", ...). */
    private val shortcut = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shortcut.value = intent?.action?.takeIf { it.startsWith(SHORTCUT_PREFIX) }
        // Opened from a reminder or a shortcut: go straight in, no launch animation.
        val skipSplash = intent?.getBooleanExtra(EXTRA_SKIP_SPLASH, false) == true || shortcut.value != null
        setContent {
            LifeAdminTheme {
                AppRoot(skipSplash, shortcut.value) { shortcut.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.action?.takeIf { it.startsWith(SHORTCUT_PREFIX) }?.let { shortcut.value = it }
    }

    companion object {
        const val EXTRA_SKIP_SPLASH = "skipSplash"
        const val SHORTCUT_PREFIX = "com.lifeadmin.app.shortcut."
    }
}

/** An empty editor draft of the given kind (for "Add reminder" style shortcuts). */
private fun blankDraft(kind: Kind, vm: AppViewModel): Draft {
    val nextHour = LocalDateTime.now().plusHours(1).withMinute(0)
    return Draft(
        title = "",
        kind = kind,
        category = Category.entries.first { it.defaultKind == kind },
        dueEpochDay = if (kind == Kind.REMINDER) nextHour.toLocalDate().toEpochDay() else LocalDate.now().toEpochDay(),
        dueMinute = if (kind == Kind.REMINDER) nextHour.hour * 60 else null,
        cycle = Cycle.NONE,
        repeatEvery = 1,
        repeatDays = "",
        repeatLimit = null,
        repeatUntilEpochDay = null,
        autoRenew = kind == Kind.REMINDER,
        amount = null,
        currency = vm.settings.currency,
        notes = "",
        alertOffsets = vm.settings.defaultAlerts(kind),
        alertStyle = vm.settings.defaultStyle(kind),
        nagEveryMin = 0,
        nagCount = 0,
    )
}

private const val TAB_ANIM = 200
private const val PUSH_ANIM = 280

@Composable
private fun AppRoot(skipSplash: Boolean, shortcut: String?, consumeShortcut: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val afterSplash = if (vm.settings.onboarded) Tab.HOME.route else "welcome"
    val start = remember { if (skipSplash) afterSplash else "splash" }
    val tabRoutes = remember { Tab.entries.map { it.route }.toSet() }

    fun goTab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(Tab.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val bar: @Composable (Tab) -> Unit = { current -> AppBottomBar(current) { goTab(it) } }

    // App-icon shortcuts.
    LaunchedEffect(shortcut) {
        val action = shortcut ?: return@LaunchedEffect
        consumeShortcut()
        if (!vm.settings.onboarded) return@LaunchedEffect
        when (action.removePrefix(MainActivity.SHORTCUT_PREFIX)) {
            "ADD_REMINDER" -> { vm.prefill.value = blankDraft(Kind.REMINDER, vm); nav.navigate("edit") }
            "ADD_PAYMENT" -> { vm.prefill.value = blankDraft(Kind.PAYMENT, vm); nav.navigate("edit") }
            "ADD_EXPIRY" -> { vm.prefill.value = blankDraft(Kind.RENEWAL, vm); nav.navigate("edit") }
            "CALENDAR" -> goTab(Tab.CALENDAR)
        }
    }

    // "How much did you pay?" when marking a payment as paid.
    val amountPrompt by vm.amountPrompt.collectAsStateWithLifecycle()
    amountPrompt?.let { item ->
        AmountPromptDialog(
            item = item,
            onConfirm = { amount ->
                vm.amountPrompt.value = null
                vm.completeWithFeedback(item, amount, skipPrompt = true)
            },
            onDismiss = { vm.amountPrompt.value = null }
        )
    }

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Tabs cross-fade; detail screens slide in from the right.
        enterTransition = {
            if (targetState.destination.route in tabRoutes && initialState.destination.route in tabRoutes) {
                fadeIn(tween(TAB_ANIM))
            } else {
                slideInHorizontally(tween(PUSH_ANIM)) { it / 4 } + fadeIn(tween(PUSH_ANIM))
            }
        },
        exitTransition = { fadeOut(tween(TAB_ANIM)) },
        popEnterTransition = { fadeIn(tween(TAB_ANIM)) },
        popExitTransition = {
            if (initialState.destination.route in tabRoutes) {
                fadeOut(tween(TAB_ANIM))
            } else {
                slideOutHorizontally(tween(PUSH_ANIM)) { it / 4 } + fadeOut(tween(PUSH_ANIM))
            }
        }
    ) {
        composable(
            "splash",
            exitTransition = { fadeOut(tween(350)) }
        ) {
            val firstTime = remember { !vm.settings.splashSeen }
            SplashScreen(durationMs = if (firstTime) 4_000 else 2_000) {
                vm.settings.splashSeen = true
                nav.navigate(afterSplash) { popUpTo("splash") { inclusive = true } }
            }
        }

        composable("welcome") {
            WelcomeScreen(onFinish = {
                vm.settings.onboarded = true
                vm.settings.askedNotificationPermission = true
                nav.navigate(Tab.HOME.route) { popUpTo("welcome") { inclusive = true } }
            })
        }

        composable(Tab.HOME.route) {
            HomeScreen(
                vm = vm,
                onAdd = { nav.navigate("edit") },
                onOpen = { id -> nav.openItem(id) },
                onSettings = { nav.navigate("settings") },
                bottomBar = { bar(Tab.HOME) }
            )
        }
        composable(Tab.CALENDAR.route) {
            CalendarScreen(vm = vm, onOpen = { nav.openItem(it) }, bottomBar = { bar(Tab.CALENDAR) })
        }
        composable(Tab.ITEMS.route) {
            ItemsScreen(
                vm = vm,
                onOpen = { nav.openItem(it) },
                onAdd = { nav.navigate("edit") },
                bottomBar = { bar(Tab.ITEMS) }
            )
        }
        composable(Tab.DONE.route) {
            DoneScreen(vm = vm, onOpen = { nav.openItem(it) }, bottomBar = { bar(Tab.DONE) })
        }
        composable(Tab.INSIGHTS.route) {
            InsightsScreen(vm = vm, onOpen = { nav.openItem(it) }, bottomBar = { bar(Tab.INSIGHTS) })
        }

        composable(
            route = "item/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            DetailScreen(
                vm = vm,
                itemId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit?id=$id") }
            )
        }
        composable(
            route = "edit?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            EditScreen(vm = vm, itemId = id, onDone = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.openItem(id: Long) {
    navigate("item/$id") { launchSingleTop = true }
}
