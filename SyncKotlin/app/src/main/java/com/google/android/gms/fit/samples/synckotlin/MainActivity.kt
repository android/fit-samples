/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.fit.samples.synckotlin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fit.samples.synckotlin.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

enum class FitActionRequestCode {
    ENABLE_REPEATED_SYNC,
    IMMEDIATE_SYNC
}

const val TAG = "FitSync"

/** Entry point for the FitSync sample application. */
class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(
            getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )

        if (lastSyncFailed()) {
            Log.i(TAG, "Last sync failed")
            setSyncStatusMessage(FAILED)
        }

        setupSyncToggle()
        setupSyncButton()
        setupWorkObserver()
    }

    /**
     * Sets up an observer for the status changes of the synchronization worker. This is used to
     * update the UI to show the status change and also enable or disable the button and switch
     * accordingly.
     */
    private fun setupWorkObserver() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(FitSyncWorker.SYNC_JOB_NAME)
            .observe(this, Observer {
                it.firstOrNull()?.apply {
                    val isImmediate = tags.contains(FitSyncWorker.IMMEDIATE_TAG)
                    setSyncStatusMessage(state)
                    setLastSuccessfulSyncDateTime()

                    binding.syncButton.isEnabled = when (state) {
                        RUNNING, FAILED -> false
                        /*
                            If the sync request is the result of the user pressing the button to do
                            it now, then that button should be disabled whilst the request is
                            enqueued. However, if the enqueued request is just from the periodic
                            scheduling, then this should be overridable with an immediate request
                            from the user, by pressing the button.
                         */
                        BLOCKED -> !isImmediate
                        ENQUEUED -> !isImmediate
                        CANCELLED, SUCCEEDED -> binding.syncToggle.isChecked
                    }

                    if (state == FAILED) {
                        disableSyncAndDisableToggle()
                    }
                }
            })
    }

    private fun setSyncStatusMessage(state: WorkInfo.State) {
        Log.i(TAG, "FitSync worker state: $state")
        binding.syncStatus.text = state.toString()
    }

    private fun setLastSuccessfulSyncDateTime() {
        binding.lastSyncText.text =
            sharedPreferences.getString(getString(R.string.last_sync_date), "never")
    }

    private fun resetLastSyncFailed() {
        sharedPreferences.edit { putBoolean(getString(R.string.last_sync_failed), false) }
    }

    private fun lastSyncFailed() =
        sharedPreferences.getBoolean(getString(R.string.last_sync_failed), false)

    private fun disableSyncAndDisableToggle() {
        disableRepeatedSync()
        binding.syncToggle.isChecked = false
    }

    private fun setupSyncButton() {
        binding.syncButton.setOnClickListener {
            binding.syncButton.isEnabled = false
            resetLastSyncFailed()
            checkPermissionsAndRun(FitActionRequestCode.IMMEDIATE_SYNC)
        }
    }

    private fun setupSyncToggle() {
        binding.syncToggle.isChecked = sharedPreferences.getBoolean(getString(R.string.sync_enabled), false)
        binding.syncToggle.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> enableRepeatedSync()
                false -> disableRepeatedSync()
            }
            binding.syncButton.isEnabled = isChecked
        }
    }

    private fun enableRepeatedSync() {
        val now = System.currentTimeMillis()
        resetLastSyncFailed()
        sharedPreferences.edit { putBoolean(getString(R.string.sync_enabled), true) }
        sharedPreferences.edit { putLong(getString(R.string.last_sync_timestamp), now) }
        checkPermissionsAndRun(FitActionRequestCode.ENABLE_REPEATED_SYNC)
    }

    private fun disableRepeatedSync() {
        sharedPreferences.edit { putBoolean(getString(R.string.sync_enabled), false) }
        val workManager = WorkManager.getInstance(this)
        workManager.cancelUniqueWork(FitSyncWorker.SYNC_JOB_NAME)
    }

    private fun checkPermissionsAndRun(fitActionRequestCode: FitActionRequestCode) {
        if (isActivityRecognitionPermissionApproved(this)) {
            fitSignIn(fitActionRequestCode)
        } else {
            requestRuntimePermissions(fitActionRequestCode)
        }
    }

    /**
     * Checks that the user is signed in, and if so, executes the specified function. If the user is
     * not signed in, initiates the sign in flow, specifying the post-sign in function to execute.
     *
     * @param requestCode The request code corresponding to the action to perform after sign in.
     */
    private fun fitSignIn(requestCode: FitActionRequestCode) {
        if (isOAuthPermissionsApproved(this, FitSyncWorker.fitnessOptions)) {
            performActionForRequestCode(requestCode)
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                    this,
                    it.ordinal,
                    getGoogleAccount(this, FitSyncWorker.fitnessOptions),
                    FitSyncWorker.fitnessOptions
                )
            }
        }
    }

    /**
     * Handles the callback from the OAuth sign in flow, executing the post sign in function
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            RESULT_OK -> {
                val postSignInAction = FitActionRequestCode.values()[requestCode]
                performActionForRequestCode(postSignInAction)
            }
            else -> {
                disableSyncAndDisableToggle()
                oAuthErrorMsg(requestCode, resultCode)
            }
        }
    }

    private fun oAuthErrorMsg(requestCode: Int, resultCode: Int) {
        Snackbar.make(
            findViewById(R.id.mainActivity),
            getString(R.string.fit_sign_in_error, requestCode, resultCode),
            Snackbar.LENGTH_INDEFINITE
        )
            .show()
    }

    /**
     * Runs the desired method, based on the specified request code. The request code is typically
     * passed to the Fit sign-in flow, and returned with the success callback. This allows the
     * caller to specify which method, post-sign-in, should be called.
     *
     * @param requestCode The code corresponding to the action to perform.
     */
    private fun performActionForRequestCode(requestCode: FitActionRequestCode) =
        when (requestCode) {
            FitActionRequestCode.ENABLE_REPEATED_SYNC -> enqueueRepeatedJob(this)
            FitActionRequestCode.IMMEDIATE_SYNC -> enqueueImmediateJob(this)
        }

    private fun requestRuntimePermissions(requestCode: FitActionRequestCode) {
        val shouldProvideRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        requestCode.let {
            if (shouldProvideRationale) {
                Log.i(TAG, "Displaying permission rationale to provide additional context.")
                Snackbar.make(
                    findViewById(R.id.mainActivity),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.ok) {
                        // Request permission
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                            requestCode.ordinal
                        )
                    }
                    .show()
            } else {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    requestCode.ordinal
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when {
            grantResults.isEmpty() -> {
                // If user interaction was interrupted, the permission request
                // is cancelled and you receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                // Permission was granted.
                val fitActionRequestCode = FitActionRequestCode.values()[requestCode]
                fitSignIn(fitActionRequestCode)
            }
            else -> {
                // Permission denied.

                // In this Activity we've chosen to notify the user that they
                // have rejected a core permission for the app since it makes the Activity useless.
                // We're communicating this message in a Snackbar since this is a sample app, but
                // core permissions would typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                disableSyncAndDisableToggle()

                Snackbar.make(
                    findViewById(R.id.mainActivity),
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.settings) {
                        // Build intent that displays the App settings screen.
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts(
                            "package",
                            BuildConfig.APPLICATION_ID, null
                        )
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    .show()
            }
        }
    }
}