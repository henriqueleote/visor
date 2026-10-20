package com.macedonga.visor.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.connection.PrompterPhase
import com.macedonga.visor.core.connection.Teleprompter
import com.macedonga.visor.core.design.Visor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What the Display's cards on the home open. */
enum class DisplayTool { TELEPROMPTER }

@HiltViewModel
class ShelfModel @Inject constructor(val script: PrompterScript, val prompter: Teleprompter) : ViewModel()

/** The Display's tools on the home: a card each, the glasses' square on it showing what the tool puts there. */
@Composable
fun DisplayShelf(onOpen: (DisplayTool) -> Unit, modifier: Modifier = Modifier, model: ShelfModel = hiltViewModel()) {
    val text by model.script.text.collectAsStateWithLifecycle()
    val look by model.script.look.collectAsStateWithLifecycle()
    val prompter by model.prompter.state.collectAsStateWithLifecycle()
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "prompter") {
            val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
            ToolCard(
                stringResource(R.string.prompter_title),
                when {
                    prompter.phase == PrompterPhase.SHOWING -> stringResource(R.string.prompter_on_glasses)
                    words == 0 -> stringResource(R.string.prompter_card_empty)
                    else -> pluralStringResource(R.plurals.prompter_words, words, words)
                },
                onClick = { onOpen(DisplayTool.TELEPROMPTER) },
            ) { ScriptPreview(text, look, Modifier.fillMaxWidth(), radius = 10.dp) }
        }
    }
}

@Composable
private fun ToolCard(title: String, status: String, onClick: () -> Unit, square: @Composable () -> Unit) {
    Column(
        Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(18.dp))
            .background(Visor.colors.raised)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        square()
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Text(title, style = Visor.type.heading, color = Visor.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(status, style = Visor.type.label, color = Visor.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val CARD_WIDTH = 172.dp
