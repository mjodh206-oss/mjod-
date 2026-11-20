package com.daraavpn.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daraavpn.app.data.model.ConnectionState
import com.daraavpn.app.data.model.PerformanceMode
import com.daraavpn.app.data.model.RegionTab
import com.daraavpn.app.data.model.ServerStatus
import com.daraavpn.app.data.model.VpnProtocol
import com.daraavpn.app.data.model.VpnServer
import com.daraavpn.app.data.model.VpnSettings
import com.daraavpn.app.data.repository.ServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val selectedTab: RegionTab = RegionTab.ARAB,
    val servers: List<VpnServer> = ServerRepository.initialServers,
    val selectedServer: VpnServer = ServerRepository.initialServers.first(),
    val searchQuery: String = "",
    val connectedSeconds: Long = 0L,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val dataUsedMb: Double = 0.0,
    val settings: VpnSettings = VpnSettings()
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun selectTab(tab: RegionTab) {
        _uiState.update { state ->
            val firstInTab = state.servers.firstOrNull { it.region == tab && it.status != ServerStatus.MAINTENANCE }
                ?: state.selectedServer
            state.copy(selectedTab = tab, selectedServer = firstInTab)
        }
    }

    fun selectServer(server: VpnServer) {
        _uiState.update { it.copy(selectedServer = server, selectedTab = server.region) }
    }

    fun selectBestServer() {
        _uiState.update { state ->
            val best = state.servers
                .filter { it.region == state.selectedTab && it.status == ServerStatus.AVAILABLE }
                .minByOrNull { it.pingMs + it.loadPercent }
                ?: state.selectedServer
            state.copy(selectedServer = best)
        }
    }

    fun toggleFavorite(serverId: String) {
        _uiState.update { state ->
            val updated = state.servers.map {
                if (it.id == serverId) it.copy(isFavorite = !it.isFavorite) else it
            }
            state.copy(servers = updated)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleConnection(onStartVpnService: (VpnServer, VpnSettings) -> Unit, onStopVpnService: () -> Unit) {
        when (_uiState.value.connectionState) {
            ConnectionState.DISCONNECTED -> connect(onStartVpnService)
            ConnectionState.CONNECTING, ConnectionState.CONNECTED -> disconnect(onStopVpnService)
        }
    }

    private fun connect(onStartVpnService: (VpnServer, VpnSettings) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
            delay(900)
            val state = _uiState.value
            onStartVpnService(state.selectedServer, state.settings)
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTED, connectedSeconds = 0L) }
            startLiveMetrics()
        }
    }

    private fun disconnect(onStopVpnService: () -> Unit) {
        timerJob?.cancel()
        onStopVpnService()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectedSeconds = 0L,
                downloadMbps = 0.0,
                uploadMbps = 0.0
            )
        }
    }

    private fun startLiveMetrics() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { state ->
                    val dl = Random.nextDouble(45.0, 148.0)
                    val ul = Random.nextDouble(18.0, 62.0)
                    state.copy(
                        connectedSeconds = state.connectedSeconds + 1,
                        downloadMbps = dl,
                        uploadMbps = ul,
                        dataUsedMb = state.dataUsedMb + (dl / 8.0) * 0.08
                    )
                }
            }
        }
    }

    fun updateSettings(transform: (VpnSettings) -> VpnSettings) {
        _uiState.update { it.copy(settings = transform(it.settings)) }
    }

    fun setProtocol(protocol: VpnProtocol) = updateSettings { it.copy(protocol = protocol) }
    fun setPerformanceMode(mode: PerformanceMode) = updateSettings { it.copy(performanceMode = mode) }
}
