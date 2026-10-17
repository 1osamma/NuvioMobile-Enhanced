package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** iOS-only custom subtitle fonts. Other platforms expose an inert implementation. */
internal class SubtitleFontPicker(
    val fonts: List<String>,
    val selected: String?,
    private val launchImport: () -> Unit,
    private val selectFont: (String?) -> Unit,
    private val deleteFont: (String) -> Unit,
) {
    fun importFont() = launchImport()
    fun select(name: String?) = selectFont(name)
    fun delete(name: String) = deleteFont(name)
}

@Composable
internal expect fun rememberSubtitleFontPicker(
    onError: (String) -> Unit,
): SubtitleFontPicker
