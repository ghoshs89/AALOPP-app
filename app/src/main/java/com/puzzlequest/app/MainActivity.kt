package com.puzzlequest.app

import android.graphics.Color as AndroidColor
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
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
data class PlayRoute(
    val studentId: String,
    val themeId: String? = null,
    val categoryKey: String? = null,
    val isDailyChallenge: Boolean = false,
)

@Serializable
data object ParentRoute

@Serializable
data class ReportRoute(val studentId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            PuzzleQuestTheme {
                PuzzleQuestApp()
            }
        }
    }
}

class AppServices(application: Application) : AndroidViewModel(application) {
    val repository = LearnerRepository(application)
    val tts = TtsController(application)
    override fun onCleared() {
        tts.shutdown()
    }
}

@Composable
private fun PuzzleQuestApp() {
    val context = LocalContext.current
    val services: AppServices = viewModel()
    val repository = services.repository
    val tts = services.tts
    var contentBank by remember { mutableStateOf<ContentBank?>(null) }
    var parentDeadline by remember { mutableLongStateOf(0L) }
    val parentUnlocked = parentDeadline > SystemClock.elapsedRealtime()
    LaunchedEffect(parentDeadline) {
        if (parentDeadline > 0L) {
            delay((parentDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            parentDeadline = 0L
        }
    }
    LifecycleResumeEffect(Unit) {
        if (parentDeadline <= SystemClock.elapsedRealtime()) parentDeadline = 0L
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        repository.refreshStudents()
        contentBank = ContentBank.load(context)
    }

    val students by repository.students.collectAsStateWithLifecycle()
    var activeStudentId by remember { mutableStateOf<String?>(null) }
    val activeStudent = students.firstOrNull { it.id == activeStudentId } ?: students.firstOrNull()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            val bank = contentBank
            val student = activeStudent
            if (bank == null || student == null) {
                LoadingBackdrop()
            } else {
                ChooseThemeScreen(
                    student = student,
                    students = students,
                    contentBank = bank,
                    repository = repository,
                    onSelectStudent = { selectedId ->
                        activeStudentId = selectedId
                    },
                    onOpenParent = {
                        parentDeadline = 0L
                        navController.navigate(ParentRoute)
                    },
                    onStartDailyChallenge = {
                        navController.navigate(PlayRoute(studentId = student.id, isDailyChallenge = true))
                    },
                    onStartDaily = {
                        navController.navigate(PlayRoute(studentId = student.id))
                    },
                    onStartTheme = { themeId ->
                        navController.navigate(PlayRoute(studentId = student.id, themeId = themeId))
                    },
                    onStartCategory = { categoryKey ->
                        navController.navigate(PlayRoute(studentId = student.id, categoryKey = categoryKey))
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
                val modeKey = when {
                    route.isDailyChallenge -> "daily_challenge"
                    route.themeId != null -> "theme_${route.themeId}"
                    route.categoryKey != null -> "cat_${route.categoryKey}"
                    else -> "daily"
                }
                val playerViewModel: PlayerViewModel = viewModel(
                    key = "player_${route.studentId}_$modeKey",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return PlayerViewModel(
                                studentId = route.studentId,
                                themeId = route.themeId,
                                categoryKey = route.categoryKey,
                                isDailyChallenge = route.isDailyChallenge,
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
                        navController.popBackStack()
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
                    unlocked = parentUnlocked,
                    onUnlockStateChange = { unlocked ->
                        parentDeadline = if (unlocked) SystemClock.elapsedRealtime() + 30 * 60_000L else 0L
                    },
                    onOpenReport = { studentId ->
                        navController.navigate(ReportRoute(studentId))
                    },
                    onBack = {
                        parentDeadline = 0L
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
            } else if (!parentUnlocked) {
                LaunchedEffect(Unit) {
                    if (!navController.popBackStack(ParentRoute, inclusive = false)) {
                        navController.navigate(ParentRoute) { popUpTo(HomeRoute) }
                    }
                }
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
