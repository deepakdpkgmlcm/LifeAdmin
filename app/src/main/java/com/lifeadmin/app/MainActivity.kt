package com.lifeadmin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifeadmin.app.ui.AppViewModel
import com.lifeadmin.app.ui.EditScreen
import com.lifeadmin.app.ui.HomeScreen
import com.lifeadmin.app.ui.SettingsScreen
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

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onAdd = { nav.navigate("edit") },
                onOpen = { id -> nav.navigate("edit?id=$id") },
                onSettings = { nav.navigate("settings") }
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
