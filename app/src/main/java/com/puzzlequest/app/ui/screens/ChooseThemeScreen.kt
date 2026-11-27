package com.puzzlequest.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import com.puzzlequest.app.R
import com.puzzlequest.app.data.content.ContentBank
import com.puzzlequest.app.data.db.StudentRecord
import com.puzzlequest.app.data.model.StrandLabels
import com.puzzlequest.app.data.repository.LearnerRepository
import com.puzzlequest.app.engine.AttemptRecord
import com.puzzlequest.app.engine.DailyChallengeEngine
import com.puzzlequest.app.engine.DailyChallengePlan
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.ProgressPointsSummary
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.ui.components.DailyChallengeCard
import com.puzzlequest.app.ui.components.StudentProgressPillRow
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors

enum class PuzzleCategoryFilter(
    val id: String,
    val labelRes: Int,
    val icon: ImageVector,
    val domainCode: String?,
) {
    ALL("all", R.string.filter_all, Icons.Filled.GridView, null),
    MATH("math", R.string.filter_math, Icons.Filled.Calculate, "MATHEMATICS"),
    LOGIC("logic", R.string.filter_logic, Icons.Filled.Psychology, "LOGIC_CS"),
    LANGUAGE("language", R.string.filter_language, Icons.Filled.Translate, "LANGUAGES"),
    WORLD("world", R.string.filter_world, Icons.Filled.Public, "HUMANITIES"),
}

private data class PuzzlePackItem(
    val id: String,
    val title: String,
    val emoji: String,
    val subtitle: String,
    val footer: String?,
    val puzzleCount: Int,
    val isHero: Boolean = false,
    val themeId: String? = null,
    val categoryKey: String? = null,
)

@Composable
fun ChooseThemeScreen(
    student: StudentRecord,
    students: List<StudentRecord>,
    contentBank: ContentBank,
    repository: LearnerRepository,
    onSelectStudent: (String) -> Unit,
    onOpenParent: () -> Unit,
    onStartDailyChallenge: () -> Unit,
    onStartDaily: () -> Unit,
    onStartTheme: (String) -> Unit,
    onStartCategory: (String) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(PuzzleCategoryFilter.ALL) }
    var showPlayerMenu by remember { mutableStateOf(false) }
    val dataVersion by repository.dataVersion.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(Dates.today()) }
    LifecycleResumeEffect(Unit) {
        today = Dates.today()
        onPauseOrDispose { }
    }
    var dailyChallengePlan by remember { mutableStateOf<DailyChallengePlan?>(null) }
    var progressSummary by remember {
        mutableStateOf(
            ProgressPointsSummary(
                totalPoints = 0,
                regularPoints = 0,
                dailyBonusPoints = 0,
                dailyCompletedCount = 0,
                dailyStreak = 0,
            ),
        )
    }

    LaunchedEffect(student.id, dataVersion, contentBank, today) {
        try {
            val attempts = repository.getAttempts(student.id)
            val pastChallenges = repository.getDailyChallenges(student.id)
            progressSummary = DailyChallengeEngine.computeProgressSummary(attempts, pastChallenges, today)
            val attemptRecords = attempts.map {
                AttemptRecord(
                    questionId = it.questionId,
                    isCorrect = it.isCorrect,
                    answeredAtMs = it.answeredAtMs,
                    localDate = it.localDate,
                )
            }
            dailyChallengePlan = DailyChallengeEngine.selectDailyChallenge(
                studentId = student.id,
                questions = contentBank.reviewedQuestions,
                skills = contentBank.skills,
                attempts = attemptRecords,
                pastChallenges = pastChallenges,
                today = today,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PuzzleQuest", "Failed to load home summary", e)
        }
    }

    val reviewed = contentBank.reviewedQuestions
    val skillsByCode = contentBank.skillsByCode
    val playableThemes = remember(contentBank) { contentBank.playableThemes() }

    fun countQuestionsForSkills(skillCodes: Set<String>): Int =
        reviewed.count { q -> q.skills.any { it in skillCodes } }

    fun buildPacks(filter: PuzzleCategoryFilter): List<PuzzlePackItem> {
        val packs = mutableListOf<PuzzlePackItem>()

        when (filter) {
            PuzzleCategoryFilter.ALL -> {
                packs += PuzzlePackItem(
                    id = "todays_puzzles",
                    title = "Today's puzzles",
                    emoji = "🧩",
                    subtitle = "Picked just for you",
                    footer = null,
                    puzzleCount = reviewed.size,
                    isHero = true,
                )
                playableThemes.forEach { theme ->
                    val skillNames = theme.skills.mapNotNull { skillsByCode[it]?.name }.take(4).joinToString(", ")
                    packs += PuzzlePackItem(
                        id = "theme_${theme.id}",
                        title = theme.name,
                        emoji = theme.emoji,
                        subtitle = skillNames,
                        footer = "Plus ${SessionSelector.THEMED_CORE_ITEMS} of today's puzzles",
                        puzzleCount = countQuestionsForSkills(theme.skills.toSet()),
                        themeId = theme.id,
                    )
                }
                // Quick-jump category packs so Math & Language are also discoverable in All
                val mathSkills = contentBank.skills.filter { it.domain == "MATHEMATICS" }.map { it.code }.toSet()
                val mathCount = countQuestionsForSkills(mathSkills)
                if (mathCount > 0) {
                    packs += PuzzlePackItem(
                        id = "domain_math",
                        title = "Math Mix",
                        emoji = "🔢",
                        subtitle = "Algebra, place value, geometry, time, money and measuring",
                        footer = "Focused math practice",
                        puzzleCount = mathCount,
                        categoryKey = "domain:MATHEMATICS",
                    )
                }
                val langSkills = contentBank.skills.filter { it.domain == "LANGUAGES" }.map { it.code }.toSet()
                val langCount = countQuestionsForSkills(langSkills)
                if (langCount > 0) {
                    packs += PuzzlePackItem(
                        id = "domain_language",
                        title = "Reading and Languages",
                        emoji = "📖",
                        subtitle = "Reading comprehension, Spanish and French vocabulary",
                        footer = "Focused language practice",
                        puzzleCount = langCount,
                        categoryKey = "domain:LANGUAGES",
                    )
                }
            }

            PuzzleCategoryFilter.MATH -> {
                val mathSkills = contentBank.skills.filter { it.domain == "MATHEMATICS" }.map { it.code }.toSet()
                packs += PuzzlePackItem(
                    id = "hero_math",
                    title = "All Math puzzles",
                    emoji = "🔢",
                    subtitle = "Adaptive mix across every math strand",
                    footer = null,
                    puzzleCount = countQuestionsForSkills(mathSkills),
                    isHero = true,
                    categoryKey = "domain:MATHEMATICS",
                )
                val mathStrands = listOf(
                    "OPERATIONS_ALGEBRA" to "➕",
                    "NUMBER_BASE_TEN" to "🔟",
                    "GEOMETRY_SPATIAL" to "📐",
                    "MEASUREMENT_DATA" to "📏",
                )
                mathStrands.forEach { (strand, emoji) ->
                    val strandSkills = contentBank.skills.filter { it.strand == strand }
                    val count = countQuestionsForSkills(strandSkills.map { it.code }.toSet())
                    if (count > 0) {
                        packs += PuzzlePackItem(
                            id = "strand_${strand.lowercase()}",
                            title = StrandLabels.detailedLabelFor(strand),
                            emoji = emoji,
                            subtitle = strandSkills.take(4).joinToString(", ") { it.name },
                            footer = null,
                            puzzleCount = count,
                            categoryKey = "strand:$strand",
                        )
                    }
                }
            }

            PuzzleCategoryFilter.LOGIC -> {
                val logicSkills = contentBank.skills.filter { it.domain == "LOGIC_CS" }
                packs += PuzzlePackItem(
                    id = "hero_logic",
                    title = "All Logic & CS puzzles",
                    emoji = "🧠",
                    subtitle = "Patterns, analogies, ordering, matrices and debugging",
                    footer = null,
                    puzzleCount = countQuestionsForSkills(logicSkills.map { it.code }.toSet()),
                    isHero = true,
                    categoryKey = "domain:LOGIC_CS",
                )
                playableThemes
                    .filter { t -> t.skills.any { skillsByCode[it]?.domain == "LOGIC_CS" } }
                    .forEach { theme ->
                        val skillNames = theme.skills.mapNotNull { skillsByCode[it]?.name }.take(4).joinToString(", ")
                        packs += PuzzlePackItem(
                            id = "theme_${theme.id}",
                            title = theme.name,
                            emoji = theme.emoji,
                            subtitle = skillNames,
                            footer = "Plus ${SessionSelector.THEMED_CORE_ITEMS} of today's puzzles",
                            puzzleCount = countQuestionsForSkills(theme.skills.toSet()),
                            themeId = theme.id,
                        )
                    }
                logicSkills.forEach { sk ->
                    val count = countQuestionsForSkills(setOf(sk.code))
                    if (count > 0) {
                        packs += PuzzlePackItem(
                            id = "skill_${sk.code.lowercase()}",
                            title = sk.name,
                            emoji = "🔍",
                            subtitle = StrandLabels.labelFor(sk.strand),
                            footer = null,
                            puzzleCount = count,
                            categoryKey = "skill:${sk.code}",
                        )
                    }
                }
            }

            PuzzleCategoryFilter.LANGUAGE -> {
                val langSkills = contentBank.skills.filter { it.domain == "LANGUAGES" }
                packs += PuzzlePackItem(
                    id = "hero_language",
                    title = "All Reading & Language puzzles",
                    emoji = "📖",
                    subtitle = "Stories, inference, Spanish and French words",
                    footer = null,
                    puzzleCount = countQuestionsForSkills(langSkills.map { it.code }.toSet()),
                    isHero = true,
                    categoryKey = "domain:LANGUAGES",
                )
                langSkills.forEach { sk ->
                    val count = countQuestionsForSkills(setOf(sk.code))
                    if (count > 0) {
                        val emoji = when {
                            sk.code.contains("ES") -> "🇪🇸"
                            sk.code.contains("FR") -> "🇫🇷"
                            else -> "📚"
                        }
                        packs += PuzzlePackItem(
                            id = "skill_${sk.code.lowercase()}",
                            title = sk.name,
                            emoji = emoji,
                            subtitle = StrandLabels.labelFor(sk.strand),
                            footer = null,
                            puzzleCount = count,
                            categoryKey = "skill:${sk.code}",
                        )
                    }
                }
            }

            PuzzleCategoryFilter.WORLD -> {
                val worldSkills = contentBank.skills.filter { it.domain == "HUMANITIES" }
                packs += PuzzlePackItem(
                    id = "hero_world",
                    title = "All World & Cars puzzles",
                    emoji = "🌍",
                    subtitle = "Flags, countries, capitals, car logos and facts",
                    footer = null,
                    puzzleCount = countQuestionsForSkills(worldSkills.map { it.code }.toSet()),
                    isHero = true,
                    categoryKey = "domain:HUMANITIES",
                )
                playableThemes
                    .filter { t -> t.skills.any { skillsByCode[it]?.domain == "HUMANITIES" } }
                    .forEach { theme ->
                        val skillNames = theme.skills.mapNotNull { skillsByCode[it]?.name }.take(4).joinToString(", ")
                        packs += PuzzlePackItem(
                            id = "theme_${theme.id}",
                            title = theme.name,
                            emoji = theme.emoji,
                            subtitle = skillNames,
                            footer = "Plus ${SessionSelector.THEMED_CORE_ITEMS} of today's puzzles",
                            puzzleCount = countQuestionsForSkills(theme.skills.toSet()),
                            themeId = theme.id,
                        )
                    }
            }
        }
        return packs
    }

    QuestBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top utility bar: subtle player badge/switcher on left (if >1 player), Grown-ups admin settings on top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (students.size > 1) {
                    Box {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(QuestColors.BgMid)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                                .clickable { showPlayerMenu = true }
                                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                                .testTag("player_switcher_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = student.avatar, fontSize = 18.sp)
                            Text(
                                text = student.name,
                                color = QuestColors.Cream,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch player",
                                tint = QuestColors.CreamDim,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showPlayerMenu,
                            onDismissRequest = { showPlayerMenu = false },
                        ) {
                            students.forEach { st ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${st.avatar} ${st.name}",
                                            fontWeight = if (st.id == student.id) FontWeight.ExtraBold else FontWeight.Medium,
                                        )
                                    },
                                    onClick = {
                                        showPlayerMenu = false
                                        onSelectStudent(st.id)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = student.avatar, fontSize = 24.sp)
                        Text(
                            text = student.name,
                            color = QuestColors.CreamDim,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                val grownUpsDesc = stringResource(R.string.grown_ups_settings_desc)
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(QuestColors.BgMid.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .clickable(onClick = onOpenParent)
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics { contentDescription = grownUpsDesc }
                        .testTag("grown_ups_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = null,
                        tint = QuestColors.CreamDim,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = stringResource(R.string.grown_ups),
                        color = QuestColors.CreamDim,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Text(
                text = stringResource(R.string.what_shall_we_play, student.name),
                style = MaterialTheme.typography.displayMedium,
                color = QuestColors.Cream,
                textAlign = TextAlign.Center,
            )

            StudentProgressPillRow(summary = progressSummary)

            dailyChallengePlan?.let { plan ->
                DailyChallengeCard(
                    plan = plan,
                    onClick = onStartDailyChallenge,
                )
            }

            // Category filter chip bar
            val filterBarDesc = stringResource(R.string.category_filter_bar_desc)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .semantics { contentDescription = filterBarDesc }
                    .testTag("category_filter_bar"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PuzzleCategoryFilter.entries.forEach { category ->
                    val isSelected = selectedFilter == category
                    val label = stringResource(category.labelRes)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = category },
                        label = {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = QuestColors.BgMid,
                            labelColor = QuestColors.CreamDim,
                            iconColor = QuestColors.CreamDim,
                            selectedContainerColor = QuestColors.Gold,
                            selectedLabelColor = QuestColors.Ink,
                            selectedLeadingIconColor = QuestColors.Ink,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.16f),
                            selectedBorderColor = QuestColors.Gold,
                            borderWidth = 1.5.dp,
                            selectedBorderWidth = 1.5.dp,
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("filter_chip_${category.id}"),
                    )
                }
            }

            AnimatedContent(
                targetState = selectedFilter,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "category_packs",
            ) { filter ->
                val currentPacks = remember(filter, contentBank) { buildPacks(filter) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (currentPacks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(QuestColors.BgMid)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Extension,
                                contentDescription = null,
                                tint = QuestColors.CreamDim,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = stringResource(R.string.no_puzzles_in_category),
                                color = QuestColors.CreamDim,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        currentPacks.forEach { pack ->
                            val onClick = {
                                when {
                                    pack.id == "todays_puzzles" -> onStartDaily()
                                    pack.themeId != null -> onStartTheme(pack.themeId)
                                    pack.categoryKey != null -> onStartCategory(pack.categoryKey)
                                    else -> onStartDaily()
                                }
                            }
                            if (pack.isHero) {
                                HeroPackCard(pack = pack, onClick = onClick)
                            } else {
                                StandardPackCard(pack = pack, onClick = onClick)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroPackCard(pack: PuzzlePackItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(QuestColors.Gold)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 26.dp)
            .testTag(if (pack.id == "todays_puzzles") "todays_puzzles_card" else "pack_card_${pack.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = pack.emoji, fontSize = 60.sp)
        Text(
            text = pack.title,
            style = MaterialTheme.typography.headlineLarge,
            color = QuestColors.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pack.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = QuestColors.Ink.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${pack.puzzleCount} puzzles available",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = QuestColors.Ink.copy(alpha = 0.72f),
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(QuestColors.Ink.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StandardPackCard(pack: PuzzlePackItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(QuestColors.BgMid)
            .clickable(onClick = onClick)
            .padding(18.dp)
            .testTag(if (pack.themeId != null) "theme_card_${pack.themeId}" else "pack_card_${pack.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = pack.emoji, fontSize = 44.sp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pack.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = QuestColors.Cream,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${pack.puzzleCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = QuestColors.Gold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(QuestColors.Gold.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            if (pack.subtitle.isNotBlank()) {
                Text(
                    text = pack.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = QuestColors.CreamDim,
                )
            }
            if (!pack.footer.isNullOrBlank()) {
                Text(
                    text = pack.footer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = QuestColors.CreamDim.copy(alpha = 0.8f),
                )
            }
        }
    }
}
