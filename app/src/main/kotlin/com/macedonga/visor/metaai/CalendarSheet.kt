package com.macedonga.visor.metaai

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.TurnRecord
import com.macedonga.visor.core.assistant.date
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.gallery.clockTime
import com.macedonga.visor.gallery.dayTitle
import com.macedonga.visor.ui.VisorIcons
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * The way through the diary: a search across every question and answer, and under it the month, with the days
 * that have something in them lit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarSheet(
    turns: List<TurnRecord>,
    shownDay: LocalDate?,
    onDay: (LocalDate) -> Unit,
    onTurn: (TurnRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Visor.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = colors.surface, scrimColor = Color.Black.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.raised)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(VisorIcons.Search, null, Modifier.size(18.dp), tint = colors.textSecondary)
                Box(Modifier.padding(start = 10.dp).weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Visor.type.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isEmpty()) Text(stringResource(R.string.meta_ai_search), style = Visor.type.body, color = colors.textSecondary)
                }
                if (query.isNotEmpty()) RoundIcon(VisorIcons.Close, stringResource(R.string.settings_clear), { query = "" }, size = 48.dp, tint = colors.textSecondary)
            }
            Crossfade(query.isBlank(), label = "calendar") { browsing ->
                if (browsing) Month(turns, shownDay, onDay) else Results(turns, query.trim(), onTurn)
            }
        }
    }
}

@Composable
private fun Month(turns: List<TurnRecord>, shownDay: LocalDate?, onDay: (LocalDate) -> Unit) {
    val colors = Visor.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val withEntries = remember(turns) { turns.map { it.date }.toSet() }
    val firstMonth = remember(turns) { turns.firstOrNull()?.date?.let(YearMonth::from) ?: YearMonth.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(shownDay ?: today)) }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) },
                style = Visor.type.heading,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            val earlier = month > firstMonth
            val later = month < YearMonth.now()
            RoundIcon(VisorIcons.ChevronLeft, stringResource(R.string.meta_ai_previous_month), { if (earlier) month = month.minusMonths(1) }, tint = if (earlier) colors.textPrimary else colors.textTertiary)
            RoundIcon(VisorIcons.Chevron, stringResource(R.string.meta_ai_next_month), { if (later) month = month.plusMonths(1) }, tint = if (later) colors.textPrimary else colors.textTertiary)
        }
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        Row(Modifier.padding(top = 12.dp)) {
            for (i in 0 until 7) {
                Text(
                    firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = Visor.type.label,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val start = month.atDay(1)
        val lead = (start.dayOfWeek.value - firstDay.value + 7) % 7
        val cells = lead + month.lengthOfMonth()
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.padding(top = 4.dp)) {
                for (d in 0 until 7) {
                    val n = week * 7 + d - lead + 1
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val date = month.atDay(n)
                            val has = date in withEntries
                            val chosen = date == shownDay
                            Column(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (chosen) colors.textPrimary else Color.Transparent)
                                    .then(if (date == today && !chosen) Modifier.border(1.dp, colors.textTertiary, CircleShape) else Modifier)
                                    .clickable(enabled = has) { onDay(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    n.toString(),
                                    style = Visor.type.body,
                                    color = when {
                                        chosen -> Color.Black
                                        has -> colors.textPrimary
                                        else -> colors.textTertiary
                                    },
                                )
                                Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (has && !chosen) colors.textSecondary else Color.Transparent))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Results(turns: List<TurnRecord>, query: String, onTurn: (TurnRecord) -> Unit) {
    val colors = Visor.colors
    val found = remember(turns, query) {
        turns.asReversed().filter { query.isNotEmpty() && (it.question.contains(query, true) || it.answer.contains(query, true)) }.take(MAX_RESULTS)
    }
    if (found.isEmpty()) {
        Text(
            stringResource(R.string.meta_ai_nothing_found),
            style = Visor.type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 12.dp)) {
        items(found, key = { it.interactionId }) { turn ->
            Column(Modifier.fillMaxWidth().clickable { onTurn(turn) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(dayTitle(turn.date) + " · " + clockTime(turn.at), style = Visor.type.label, color = colors.textTertiary)
                Text(marked(turn.question, query), style = Visor.type.body, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (turn.answer.isNotEmpty()) {
                    Text(marked(turn.answer, query), style = Visor.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** [text] with every [query] in it picked out. */
@Composable
private fun marked(text: String, query: String): AnnotatedString {
    val mark = SpanStyle(color = Visor.colors.textPrimary, background = Visor.colors.control)
    return buildAnnotatedString {
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            append(text.substring(from, at))
            withStyle(mark) { append(text.substring(at, at + query.length)) }
            from = at + query.length
        }
        append(text.substring(from))
    }
}

private const val MAX_RESULTS = 60
