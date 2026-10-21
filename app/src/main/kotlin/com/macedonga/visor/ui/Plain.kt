package com.macedonga.visor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macedonga.visor.core.design.Visor

/**
 * The test client's whole visual vocabulary: a scrolling column, a section label, and a label with
 * a value.
 */
@Composable
fun PlainScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Visor.colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = Visor.type.title, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
        content()
    }
}

@Composable
fun Section(text: String) {
    Text(
        text.uppercase(),
        style = Visor.type.label,
        color = Visor.colors.textSecondary,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
fun Line(text: String, secondary: Boolean = false) {
    Text(
        text,
        style = Visor.type.body,
        color = if (secondary) Visor.colors.textSecondary else Visor.colors.textPrimary,
    )
}

@Composable
fun Reading(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.weight(0.4f))
        Text(value ?: "unknown", style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(0.6f))
    }
}
