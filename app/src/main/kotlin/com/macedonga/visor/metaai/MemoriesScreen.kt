package com.macedonga.visor.metaai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.gallery.dayTitle
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.asHeading
import com.macedonga.visor.ui.rememberHaptics
import java.time.Instant
import java.time.ZoneId

/** What the assistant keeps about the wearer: read, dropped, or added by hand. */
@Composable
fun MemoriesScreen(model: MetaAiModel = hiltViewModel()) {
    val colors = Visor.colors
    val haptics = rememberHaptics()
    val memories by model.memories.collectAsStateWithLifecycle()
    val forgotten by model.forgotten.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf("") }

    fun add() {
        if (adding.isBlank()) return
        haptics.toggle()
        model.remember(adding.trim())
        adding = ""
    }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(horizontal = SIDE).padding(top = 20.dp, bottom = 20.dp)) {
                    Text(stringResource(R.string.meta_ai_memories), style = Visor.type.title, color = colors.textPrimary, modifier = Modifier.asHeading())
                    Text(stringResource(R.string.memories_sub), style = Visor.type.body, color = colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (memories.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.memories_empty), style = ledeStyle(), color = colors.textTertiary, modifier = Modifier.padding(horizontal = SIDE, vertical = 12.dp))
                }
            }
            items(memories.asReversed(), key = { it.id }) { memory ->
                Row(
                    Modifier.animateItem().fillMaxWidth().padding(start = SIDE, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(memory.text, style = ledeStyle(), color = colors.textPrimary)
                        val day = Instant.ofEpochMilli(memory.at).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(stringResource(R.string.memories_since, dayTitle(day)), style = Visor.type.label, color = colors.textTertiary, modifier = Modifier.padding(top = 4.dp))
                    }
                    RoundIcon(VisorIcons.Close, stringResource(R.string.memories_forget), {
                        haptics.toggle()
                        model.forget(memory)
                    }, size = 40.dp, tint = colors.textSecondary)
                }
            }
        }

        AnimatedVisibility(
            visible = forgotten != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(160)),
        ) {
            UndoPill(stringResource(R.string.memories_deleted)) {
                haptics.toggle()
                model.unforget()
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to colors.canvas.copy(alpha = 0.92f), 1f to colors.canvas))
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(colors.raised).padding(horizontal = 20.dp, vertical = 15.dp),
            ) {
                BasicTextField(
                    value = adding,
                    onValueChange = { adding = it },
                    textStyle = Visor.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (adding.isEmpty()) Text(stringResource(R.string.memories_add), style = Visor.type.body, color = colors.textSecondary)
            }
            Spacer(Modifier.width(10.dp))
            val can = adding.isNotBlank()
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (can) colors.textPrimary else colors.control), contentAlignment = Alignment.Center) {
                RoundIcon(VisorIcons.Plus, stringResource(R.string.memories_add), { add() }, size = 52.dp, tint = if (can) Color.Black else colors.textSecondary)
            }
        }
    }
}
