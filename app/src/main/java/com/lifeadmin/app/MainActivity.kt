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
import com.lifeadmin.app.ui.EditScreen
import com.lifeadmin.app.ui.HomeScreen
import com.lifeadmin.app.ui.InsightsScreen
import com.lifeadmin.app.ui.ItemsScreen
import com.lifeadmin.app.ui.SettingsScreen
import com.lifeadmin.app.ui.Tab
import com.lifeadmin.app.ui.WelcomeScreen
import com.lifeadmin.app.ui.theme.LifeAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeAdminTheme {
                AppRoot()
            }
        }
    }
}

private const val TAB_ANIM = 200
private const val PUSH_ANIM = 280

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val start = remember { if (vm.settings.onboarded) Tab.HOME.route else "welcome" }
    val tabRoutes = remember { Tab.entries.map { it.route }.toSet() }

    fun goTab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(Tab.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val bar: @Composable (Tab) -> Unit = { current -> AppBottomBar(current) { goTab(it) } }

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
