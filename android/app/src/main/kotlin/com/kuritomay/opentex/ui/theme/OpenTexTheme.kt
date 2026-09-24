package com.kuritomay.opentex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable fun OpenTexTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = OpenTexDark, typography = OpenTexTypography, shapes = OpenTexShapes, content = content)
