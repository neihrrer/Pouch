package dev.trove.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.trove.app.ui.home.HomeScreen
import dev.trove.app.ui.reader.ReaderScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavHost(
    factory: ViewModelProvider.Factory,
    onShareUrl: (String) -> Unit,
    pendingShareUrl: StateFlow<String?>,
    onPendingShareHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val pending by pendingShareUrl.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenArticle = { id -> navController.navigate("reader/$id") },
                onShareUrl = onShareUrl,
                pendingShareUrl = pending,
                onPendingShareHandled = onPendingShareHandled,
                factory = factory,
            )
        }
        composable(
            route = "reader/{articleId}",
            arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
        ) { entry ->
            val articleId = entry.arguments?.getLong("articleId") ?: 0L
            ReaderScreen(
                articleId = articleId,
                onBack = { navController.popBackStack() },
                factory = factory,
            )
        }
    }
}
