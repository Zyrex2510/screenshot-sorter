package com.zyrex.screenshotsorter.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyrex.screenshotsorter.platform.SetupState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SorterTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val activity = this
                val storageRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    // After a second refusal Android stops asking, so the only way left is the app's settings page.
                    if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        SetupState.openFirst(activity, listOf(SetupState.appDetailsIntent(activity)))
                    }
                    viewModel.refresh()
                }
                val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        SetupState.openFirst(activity, SetupState.notificationSettingsIntents(activity))
                    }
                    viewModel.refresh()
                }
                val actions = remember {
                    MainActions(
                        onToggle = viewModel::setEnabled,
                        onGrantStorage = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                SetupState.openFirst(activity, SetupState.allFilesAccessIntents(activity))
                            } else {
                                storageRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                        onGrantUsage = { SetupState.openFirst(activity, SetupState.usageAccessIntents(activity)) },
                        onAllowBackground = { SetupState.openFirst(activity, SetupState.batteryIntents(activity)) },
                        onOpenAutostart = {
                            SetupState.openFirst(
                                activity,
                                SetupState.autostartIntents(activity) + SetupState.appDetailsIntent(activity),
                            )
                        },
                        onAllowNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onSortOld = viewModel::countOld,
                        onConfirmSortOld = viewModel::confirmOld,
                        onDismissSortOld = viewModel::dismissOld,
                    )
                }
                MainScreen(ui, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
