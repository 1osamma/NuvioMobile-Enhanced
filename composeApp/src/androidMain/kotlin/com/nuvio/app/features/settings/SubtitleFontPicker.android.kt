package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberSubtitleFontPicker(onError: (String) -> Unit): SubtitleFontPicker =
    SubtitleFontPicker(emptyList(), null, {}, {}, {})
