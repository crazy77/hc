package com.fanta.healthconnect.presentation

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
import androidx.lifecycle.lifecycleScope
import com.fanta.healthconnect.data.repository.HealthConnectRepository
import com.fanta.healthconnect.presentation.screen.MainScreen
import com.fanta.healthconnect.presentation.theme.HealthConnectWebhookTheme
import com.fanta.healthconnect.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

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
    

    
    private var currentViewModel: MainViewModel? = null
    
    private val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { grantedPermissions ->
        // 권한 요청 결과를 처리
        android.util.Log.d("MainActivity", "권한 요청 결과: $grantedPermissions")
        android.util.Log.d("MainActivity", "권한 요청 완료 - 허용된 권한 수: ${grantedPermissions.size}")
        
        // ViewModel이 있으면 권한 상태 새로고침
        currentViewModel?.let { viewModel ->
            android.util.Log.d("MainActivity", "ViewModel 참조 확인됨, 권한 상태 새로고침 시작")
            viewModel.refreshPermissionStatus()
            
            // 권한이 허용되었으면 즉시 UI 업데이트
            if (grantedPermissions.isNotEmpty()) {
                android.util.Log.d("MainActivity", "권한 허용됨 - UI 상태 즉시 업데이트")
            } else {
                android.util.Log.d("MainActivity", "권한 거부됨 - 사용자에게 알림")
            }
        } ?: run {
            android.util.Log.e("MainActivity", "ViewModel 참조가 null입니다!")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            HealthConnectWebhookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: MainViewModel = hiltViewModel()
                    currentViewModel = viewModel
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            // 권한 요청을 직접 처리
                            android.util.Log.d("MainActivity", "권한 요청 시작")
                            
                            // 누락된 권한들만 요청
                            lifecycleScope.launch {
                                try {
                                    val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
                                    val missingPermissions = healthConnectRepository.permissions.filter { it !in grantedPermissions }.toSet()
                                    
                                    android.util.Log.d("MainActivity", "이미 허용된 권한: ${grantedPermissions.joinToString(", ")}")
                                    android.util.Log.d("MainActivity", "누락된 권한: ${missingPermissions.joinToString(", ")}")
                                    
                                    if (missingPermissions.isNotEmpty()) {
                                        requestPermissions.launch(missingPermissions)
                                        android.util.Log.d("MainActivity", "누락된 권한 요청 다이얼로그 실행됨")
                                    } else {
                                        android.util.Log.d("MainActivity", "모든 권한이 이미 허용됨")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "권한 요청 실패", e)
                                }
                            }
                        },
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
    

}