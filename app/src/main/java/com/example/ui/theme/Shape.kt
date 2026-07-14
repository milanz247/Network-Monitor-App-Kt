package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Consistent corner-radius tokens used across cards, chips and the hero card. */
object AppShapes {
    val chip = RoundedCornerShape(100)
    val small = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(32.dp)
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
