package com.puzzlequest.app

import android.app.Application
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val backgroundScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("PuzzleQuest", "Background task failed", e)
        },
    )

    private val _contentBank = MutableStateFlow<ContentBank?>(null)
    val contentBank: StateFlow<ContentBank?> = _contentBank.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private val _activeStudentId = MutableStateFlow<String?>(null)
    val activeStudentId: StateFlow<String?> = _activeStudentId.asStateFlow()

    private val _parentDeadline = MutableStateFlow(0L)
    val parentDeadline: StateFlow<Long> = _parentDeadline.asStateFlow()

    init {
        loadAppData()
    }

    fun loadAppData() {
        viewModelScope.launch {
            _loadError.value = false
            try {
                repository.refreshStudents()
                if (_contentBank.value == null) {
                    _contentBank.value = ContentBank.load(getApplication())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PuzzleQuest", "Failed to load app data", e)
                _loadError.value = true
            }
        }
    }

    fun selectStudent(studentId: String) {
        _activeStudentId.value = studentId
    }

    fun setParentUnlocked(unlocked: Boolean) {
        _parentDeadline.value = if (unlocked) SystemClock.elapsedRealtime() + 30 * 60_000L else 0L
    }

    fun expireParentIfNeeded() {
        if (_parentDeadline.value <= SystemClock.elapsedRealtime()) {
            _parentDeadline.value = 0L
        }
    }

    override fun onCleared() {
        tts.shutdown()
        backgroundScope.cancel()
    }
}

@Composable
private fun PuzzleQuestApp() {
    val services: AppServices = viewModel()
    val repository = services.repository
    val tts = services.tts
    val contentBank by services.contentBank.collectAsStateWithLifecycle()
    val loadError by services.loadError.collectAsStateWithLifecycle()
    val parentDeadline by services.parentDeadline.collectAsStateWithLifecycle()
    val parentUnlocked = parentDeadline > SystemClock.elapsedRealtime()

    LaunchedEffect(parentDeadline) {
        if (parentDeadline > 0L) {
            delay((parentDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            services.setParentUnlocked(false)
        }
    }
    LifecycleResumeEffect(Unit) {
        services.expireParentIfNeeded()
        onPauseOrDispose { }
    }

    val students by repository.students.collectAsStateWithLifecycle()
    val activeStudentId by services.activeStudentId.collectAsStateWithLifecycle()
    val activeStudent = students.firstOrNull { it.id == activeStudentId } ?: students.firstOrNull()
    val navController = rememberNavController()

    if (loadError && contentBank == null) {
        QuestBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = "Could not load puzzles. Tap below to try again.",
                    color = QuestColors.Cream,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { services.loadAppData() }) {
                    Text("Retry")
                }
            }
        }
    } else {
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
                        services.selectStudent(selectedId)
                    },
                    onOpenParent = {
                        services.setParentUnlocked(false)
                        navController.navigate(ParentRoute) { launchSingleTop = true }
                    },
                    onStartDailyChallenge = {
                        navController.navigate(PlayRoute(studentId = student.id, isDailyChallenge = true)) {
                            launchSingleTop = true
                        }
                    },
                    onStartDaily = {
                        navController.navigate(PlayRoute(studentId = student.id)) {
                            launchSingleTop = true
                        }
                    },
                    onStartTheme = { themeId ->
                        navController.navigate(PlayRoute(studentId = student.id, themeId = themeId)) {
                            launchSingleTop = true
                        }
                    },
                    onStartCategory = { categoryKey ->
                        navController.navigate(PlayRoute(studentId = student.id, categoryKey = categoryKey)) {
                            launchSingleTop = true
                        }
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
                                backgroundScope = services.backgroundScope,
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
                        services.setParentUnlocked(unlocked)
                    },
                    onOpenReport = { studentId ->
                        navController.navigate(ReportRoute(studentId)) { launchSingleTop = true }
                    },
                    onBack = {
                        services.setParentUnlocked(false)
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
}

@Composable
private fun LoadingBackdrop() {
    QuestBackdrop {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = QuestColors.Gold)
        }
    }
}
