package com.rohit.voicepause.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rohit.voicepause.ui.screen.Screen
import com.rohit.voicepause.ui.theme.BackgroundDark
import com.rohit.voicepause.ui.theme.PausifyRed
import com.rohit.voicepause.ui.theme.TextSecondary

@Composable
fun PausifyBottomNav(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.Controls,
        Screen.Settings
    )
    
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    Surface(
        color = BackgroundDark,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val contentColor = if (isSelected) PausifyRed else TextSecondary
                
                // Animate the line width from 0 to 24dp
                val lineWidth by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "lineWidth"
                )

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Removes the default ripple highlight
                        ) {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = contentColor,
                            modifier = Modifier.size(26.dp)
                        )
                        
                        Spacer(Modifier.height(6.dp))
                        
                        // Animated red line
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(lineWidth)
                                .background(
                                    color = if (lineWidth > 0.dp) PausifyRed else Color.Transparent,
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
