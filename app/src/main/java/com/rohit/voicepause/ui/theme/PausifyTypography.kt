package com.rohit.voicepause.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.rohit.voicepause.R

val PausifyTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily(Font(R.font.montserrat_semibold)),
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily(Font(R.font.montserrat_medium)),
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily(Font(R.font.inter_regular)),
        fontSize = 15.sp
    )
)
