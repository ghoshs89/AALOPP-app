package com.puzzlequest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.puzzlequest.app.data.content.ContentBank
import com.puzzlequest.app.data.repository.LearnerRepository
import com.puzzlequest.app.engine.TtsController
import com.puzzlequest.app.ui.screens.ChooseThemeScreen
import com.puzzlequest.app.ui.screens.HomeScreen
import com.puzzlequest.app.ui.screens.ParentScreen
import com.puzzlequest.app.ui.screens.PlayerScreen
import com.puzzlequest.app.ui.screens.PlayerViewModel
import com.puzzlequest.app.ui.screens.ReportScreen
import com.puzzlequest.app.ui.theme.PuzzleQuestTheme
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChooseRoute(val studentId: String)

@Serializable
data class PlayRoute(val studentId: String, val themeId: String? = null)

@Serializable
data object ParentRoute

@Serializable
data class ReportRoute(val studentId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuzzleQuestTheme {
                PuzzleQuestApp()
            }
        }
    }
}

@Composable
private fun PuzzleQuestApp() {
    val context = LocalContext.current
    val repository = remember { LearnerRepository(context) }
    val tts = remember { TtsController(context) }
    var contentBank by remember { mutableStateOf<ContentBank?>(null) }
    var parentUnlocked by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(Unit) {
        repository.refreshStudents()
        contentBank = ContentBank.load(context)
    }

    val students by repository.students.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                students = students,
                isLoadingContent = contentBank == null,
                onSelectStudent = { studentId ->
                    navController.navigate(ChooseRoute(studentId))
                },
                onOpenParent = {
                    navController.navigate(ParentRoute)
                },
            )
        }

        composable<ChooseRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChooseRoute>()
            val bank = contentBank
            val student = students.firstOrNull { it.id == route.studentId }
            if (bank == null || student == null) {
                LoadingBackdrop()
            } else {
                ChooseThemeScreen(
                    student = student,
                    themes = remember(bank) { bank.playableThemes() },
                    skillsByCode = bank.skillsByCode,
                    onStartDaily = {
                        navController.navigate(PlayRoute(studentId = student.id, themeId = null))
                    },
                    onStartTheme = { themeId ->
                        navController.navigate(PlayRoute(studentId = student.id, themeId = themeId))
                    },
                    onBackToPlayers = {
                        navController.popBackStack(HomeRoute, inclusive = false)
                    },
                )
            }
        }

        composable<PlayRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayRoute>()
            val bank = contentBank
            if (bank == null) {
                LoadingBackdrop()
            } else {
                val playerViewModel: PlayerViewModel = viewModel(
                    key = "player_${route.studentId}_${route.themeId ?: "daily"}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return PlayerViewModel(
                                studentId = route.studentId,
                                themeId = route.themeId,
                                contentBank = bank,
                                repository = repository,
                                tts = tts,
                            ) as T
                        }
                    },
                )
                PlayerScreen(
                    viewModel = playerViewModel,
                    onLeave = {
                        navController.popBackStack(HomeRoute, inclusive = false)
                    },
                )
            }
        }

        composable<ParentRoute> {
            val bank = contentBank
            if (bank == null) {
                LoadingBackdrop()
            } else {
                ParentScreen(
                    contentBank = bank,
                    repository = repository,
                    initiallyUnlocked = parentUnlocked,
                    onUnlockStateChange = { parentUnlocked = it },
                    onOpenReport = { studentId ->
                        navController.navigate(ReportRoute(studentId))
                    },
                    onBack = {
                        navController.popBackStack(HomeRoute, inclusive = false)
                    },
                )
            }
        }

        composable<ReportRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReportRoute>()
            val bank = contentBank
            if (bank == null) {
                LoadingBackdrop()
            } else {
                ReportScreen(
                    studentId = route.studentId,
                    contentBank = bank,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun LoadingBackdrop() {
    QuestBackdrop {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = QuestColors.Gold)
        }
    }
}
