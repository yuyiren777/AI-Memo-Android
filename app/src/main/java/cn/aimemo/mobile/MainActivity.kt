package cn.aimemo.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.aimemo.mobile.reminder.NotificationHelper
import cn.aimemo.mobile.ui.AiMemoApp
import cn.aimemo.mobile.ui.AppViewModel
import cn.aimemo.mobile.ui.theme.AiMemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val state by appViewModel.uiState.collectAsState()
            var launchReminderShown by remember { mutableStateOf(false) }
            var notificationPermissionGranted by remember {
                mutableStateOf(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> notificationPermissionGranted = granted }

            LaunchedEffect(Unit) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(state.loading, state.schedules, notificationPermissionGranted) {
                if (!state.loading && notificationPermissionGranted && !launchReminderShown) {
                    launchReminderShown = true
                    NotificationHelper.showUndatedSummary(
                        this@MainActivity,
                        state.schedules.filter { !it.completed && it.date == null },
                    )
                }
            }

            AiMemoTheme(darkTheme = state.darkMode) {
                AiMemoApp(viewModel = appViewModel, state = state)
            }
        }
    }
}
