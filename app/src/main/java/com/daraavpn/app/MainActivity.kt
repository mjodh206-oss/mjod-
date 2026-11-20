package com.daraavpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daraavpn.app.data.model.VpnServer
import com.daraavpn.app.data.model.VpnSettings
import com.daraavpn.app.ui.screens.AboutScreen
import com.daraavpn.app.ui.screens.HomeScreen
import com.daraavpn.app.ui.screens.OnboardingScreen
import com.daraavpn.app.ui.screens.ServersScreen
import com.daraavpn.app.ui.screens.SettingsScreen
import com.daraavpn.app.ui.screens.SplashScreen
import com.daraavpn.app.ui.theme.DaraaVPNTheme
import com.daraavpn.app.ui.viewmodel.MainViewModel
import com.daraavpn.app.vpn.DaraaVpnService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingVpnStart: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnStart?.invoke()
        }
        pendingVpnStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val navController = rememberNavController()

            DaraaVPNTheme(isRtl = uiState.settings.isArabic) {
                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen(onFinished = {
                            navController.navigate("onboarding") {
                                popUpTo("splash") { inclusive = true }
                            }
                        })
                    }
                    composable("onboarding") {
                        OnboardingScreen(onFinish = {
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            uiState = uiState,
                            onTabSelected = viewModel::selectTab,
                            onToggleConnect = {
                                viewModel.toggleConnection(
                                    onStartVpnService = { server, settings ->
                                        requestAndStartVpn(server, settings)
                                    },
                                    onStopVpnService = { stopVpnService() }
                                )
                            },
                            onOpenServers = { navController.navigate("servers") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenAbout = { navController.navigate("about") }
                        )
                    }
                    composable("servers") {
                        ServersScreen(
                            uiState = uiState,
                            onSearchChange = viewModel::updateSearchQuery,
                            onSelectBest = viewModel::selectBestServer,
                            onSelectServer = viewModel::selectServer,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = uiState.settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onSelectProtocol = viewModel::setProtocol,
                            onSelectMode = viewModel::setPerformanceMode,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun requestAndStartVpn(server: VpnServer, settings: VpnSettings) {
        val startAction = {
            val intent = Intent(this, DaraaVpnService::class.java).apply {
                action = DaraaVpnService.ACTION_CONNECT
                putExtra(DaraaVpnService.EXTRA_SERVER_NAME, server.countryNameAr)
                putExtra(DaraaVpnService.EXTRA_DNS_PROTECTION, settings.dnsProtection)
            }
            ContextCompat.startForegroundService(this, intent)
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingVpnStart = startAction
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startAction()
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, DaraaVpnService::class.java).apply {
            action = DaraaVpnService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}
