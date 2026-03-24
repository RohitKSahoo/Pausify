package com.rohit.voicepause

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rohit.voicepause.ui.components.PausifyBottomNav
import com.rohit.voicepause.ui.screen.*
import com.rohit.voicepause.ui.theme.VoicePauseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Settings.migrate(this)

        setContent {
            VoicePauseTheme {
                val navController = rememberNavController()
                val isRunning by VoiceMonitorService.isRunningFlow.collectAsState()
                val scope = rememberCoroutineScope()

                // State to track if the Live Monitor (ActivityScreen) is explicitly opened
                var showLiveMonitor by remember { mutableStateOf(false) }

                // The pages in our horizontal pager
                val pages = listOf(Screen.Home, Screen.Controls, Screen.Settings)
                val pagerState = rememberPagerState(pageCount = { pages.size })

                // Sync Pager with Bottom Nav selection
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Handle system back button when Live Monitor is open
                BackHandler(enabled = showLiveMonitor) {
                    showLiveMonitor = false
                }

                Scaffold(
                    bottomBar = {
                        // Only show bottom nav if Live Monitor is NOT open
                        if (!showLiveMonitor) {
                            PausifyBottomNav(
                                currentRoute = pages[pagerState.currentPage].route,
                                onItemSelected = { screen ->
                                    val targetIndex = pages.indexOf(screen)
                                    if (targetIndex != -1) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(targetIndex)
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(if (showLiveMonitor) PaddingValues(0.dp) else innerPadding)) {
                        // Main content pager
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !showLiveMonitor // Disable swiping when monitor is open
                        ) { pageIndex ->
                            when (pages[pageIndex]) {
                                Screen.Home -> {
                                    // Slot 0 is always HomeScreen now
                                    HomeScreen(
                                        isRunning = isRunning,
                                        onToggleService = {
                                            if (isRunning) stopVoicePause()
                                            else requestPermissionsAndStart()
                                        }
                                    )
                                }
                                Screen.Controls -> {
                                    ControlsScreen(
                                        onOpenActivity = {
                                            showLiveMonitor = true
                                        }
                                    )
                                }
                                Screen.Settings -> {
                                    SettingsScreen(onBack = {
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    })
                                }
                                else -> Unit
                            }
                        }

                        // Overlay for Activity Screen (Live Monitor) with slide up/down animation
                        AnimatedVisibility(
                            visible = showLiveMonitor,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(durationMillis = 500)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(durationMillis = 500)
                            ) + fadeOut()
                        ) {
                            ActivityScreen(
                                onEndSession = { 
                                    stopVoicePause()
                                    showLiveMonitor = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ======================
    // PERMISSION & SERVICE
    // ======================

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                startVoicePauseInternal()
            }
        }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startVoicePauseInternal() {
        val intent = Intent(this, VoiceMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopVoicePause() {
        stopService(Intent(this, VoiceMonitorService::class.java))
    }
}
