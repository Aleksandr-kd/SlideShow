package com.example.slideshow

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability

private const val TAG = "AppUpdate"

@Composable
fun AppUpdateHelper() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val updateManager = remember { RuStoreAppUpdateManagerFactory.create(context) }

    DisposableEffect(updateManager) {
        val listener = InstallStateUpdateListener { installState ->
            when (installState.installStatus) {
                InstallStatus.DOWNLOADING -> {}
                InstallStatus.DOWNLOADED -> {
                    updateManager.completeUpdate(
                        AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()
                    ).addOnFailureListener { e ->
                        Log.e(TAG, "completeUpdate error", e)
                    }
                }
                InstallStatus.FAILED -> {
                    Log.e(TAG, "Download failed: code=${installState.installErrorCode}")
                }
                else -> {}
            }
        }
        updateManager.registerListener(listener)
        onDispose { updateManager.unregisterListener(listener) }
    }

    LaunchedEffect(Unit) {
        updateManager.getAppUpdateInfo()
            .addOnSuccessListener { info ->
                if (info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
                    updateManager.startUpdateFlow(
                        info,
                        AppUpdateOptions.Builder().build()
                    ).addOnFailureListener { e ->
                        Log.e(TAG, "startUpdateFlow error", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getAppUpdateInfo error", e)
            }
    }
}
