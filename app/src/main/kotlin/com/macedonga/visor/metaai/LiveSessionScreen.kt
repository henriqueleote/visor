package com.macedonga.visor.metaai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.date
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.gallery.clockTime
import com.macedonga.visor.gallery.dayTitle
import com.macedonga.visor.ui.asHeading

/** One Live AI session in full: each thing asked, the frame the camera had then, and the answer. */
@Composable
fun LiveSessionScreen(conversation: String, onMemories: () -> Unit, model: MetaAiModel = hiltViewModel()) {
    val colors = Visor.colors
    val all by model.turns.collectAsStateWithLifecycle()
    val turns = remember(all, conversation) { all.filter { it.conversation == conversation } }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(horizontal = SIDE).padding(top = 20.dp, bottom = 24.dp)) {
                    Text(stringResource(R.string.meta_ai_live_ai), style = Visor.type.title, color = colors.textPrimary, modifier = Modifier.asHeading())
                    turns.firstOrNull()?.let {
                        Text(dayTitle(it.date) + ", " + clockTime(it.at), style = Visor.type.body, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            itemsIndexed(turns, key = { _, t -> t.interactionId }) { i, turn ->
                if (i > 0) Spacer(Modifier.height(28.dp))
                Exchange(turn, Int.MAX_VALUE, onLongPress = {}, onPhoto = { viewing = it }, onMemories = onMemories)
            }
            item(key = "foot") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
    viewing?.let { PhotoView(it) { viewing = null } }
}
