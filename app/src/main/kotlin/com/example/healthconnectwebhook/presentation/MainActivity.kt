package com.example.healthconnectwebhook.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthconnectwebhook.data.repository.HealthConnectRepository
import com.example.healthconnectwebhook.presentation.screen.MainScreen
import com.example.healthconnectwebhook.presentation.theme.HealthConnectWebhookTheme
import com.example.healthconnectwebhook.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var healthConnectRepository: HealthConnectRepository
    
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(this)
    }
    
    // Health Connect 권한 요청을 위한 Activity Result Launcher
    private val requestPermissionActivityContract = 
        PermissionController.createRequestPermissionResultContract()
    
    private lateinit var mainViewModel: MainViewModel
    
    private val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
        // 권한 요청 결과를 처리하고 ViewModel에 알림
        mainViewModel.refreshPermissionStatus()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthConnectWebhookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    mainViewModel = hiltViewModel()
                    MainScreen(
                        viewModel = mainViewModel,
                        onRequestPermissions = ::requestHealthConnectPermissions,
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
    
    private fun requestHealthConnectPermissions() {
        requestPermissions.launch(healthConnectRepository.permissions)
    }
}