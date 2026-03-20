package com.rohit.voicepause.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color.DarkGray.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = screen.title.uppercase(),
                            color = contentColor,
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (isSelected) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .background(PausifyRed)
                            )
                        }
                    }
                }
            }
        }
    }
}
