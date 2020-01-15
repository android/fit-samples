/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.fit.samples.basicrecordingapikotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.TextViewCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fit.samples.common.logger.Log
import com.google.android.gms.fit.samples.common.logger.LogView
import com.google.android.gms.fit.samples.common.logger.LogWrapper
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.material.snackbar.Snackbar

const val TAG = "BasicRecordingApi"

/**
 * This sample demonstrates how to use the Recording API of the Google Fit platform to subscribe
 * to data sources, query against existing subscriptions, and remove subscriptions. It also
 * demonstrates how to authenticate a user with Google Play Services.
 *
 * Because the subscription is for data type TYPE_CALORIES_EXPENDED, on Android 10 the Android
 * ACTIVITY_RECOGNITION permission is required. This sample shows how to check for and request the
 * permission when necessary.
 */
class MainActivity : AppCompatActivity() {
    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
                .build()
    }

    // Two maps are used in conjunction with the OAuth flow. This is in order to identify what
    // function to execute once the flow has successfully completed.
    private val signInActionMap = mapOf<() -> Any, Int>(
            ::subscribe to 1,
            ::dumpSubscriptionsList to 2,
            ::cancelSubscription to 3
    )
    private val signInResponseMap = signInActionMap.entries.associate { (k, v) -> v to k }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging()

        checkPermissionsAndRun(::subscribe)
    }

    private fun checkPermissionsAndRun(signInAction: () -> Any) {
        if (!requiresActivityRecognitionPermission() || hasRuntimePermission()) {
            fitSignIn(signInAction)
        } else {
            requestRuntimePermissions(signInAction)
        }
    }

    private fun requiresActivityRecognitionPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Checks that the user is signed in, and if so, executes the specified function. If the user is
     * not signed in, initiates the sign in flow, specifying the post-sign in function to execute.
     *
     * @param signInAction The function to execute if already signed in, or once signed in.
     */
    private fun fitSignIn(signInAction: () -> Any) {
        if (!hasOAuthPermissions()) {
            val requestCode = signInActionMap[signInAction]
            requestCode?.let {
                GoogleSignIn.requestPermissions(
                        this,
                        requestCode,
                        getGoogleAccount(), fitnessOptions)
            }
        } else signInAction()
    }

    /**
     * Handles the callback from the OAuth sign in flow, executing the post sign in function
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            RESULT_OK -> {
                val postSignInAction = signInResponseMap[requestCode]
                postSignInAction?.let {
                    postSignInAction()
                }
            }
            else -> oAuthErrorMsg(requestCode, resultCode)
        }
    }

    private fun oAuthErrorMsg(requestCode: Int, resultCode: Int) {
        val message = """
            There was an error signing into Fit. Check the troubleshooting section of the README
            for potential issues.
            Request code was: $requestCode
            Result code was: $resultCode
        """.trimIndent()
        Log.e(TAG, message)
    }

    private fun hasOAuthPermissions() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    /**
     * Subscribes to an available [DataType]. Subscriptions can exist across application
     * instances (so data is recorded even after the application closes down).  When creating
     * a new subscription, it may already exist from a previous invocation of this app.  If
     * the subscription already exists, the method is a no-op.  However, you can check this with
     * a special success code.
     */
    private fun subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        // [START subscribe_to_datatype]
        Fitness.getRecordingClient(this, getGoogleAccount())
                .subscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener { Log.i(TAG, "Successfully subscribed!") }
                .addOnFailureListener { Log.i(TAG, "There was a problem subscribing.") }
        // [END subscribe_to_datatype]
    }

    /**
     * Fetches a list of all active subscriptions and log it. Since the logger for this sample
     * also prints to the screen, we can see what is happening in this way.
     */
    private fun dumpSubscriptionsList() {
        // [START list_current_subscriptions]
        Fitness.getRecordingClient(this, getGoogleAccount())
                .listSubscriptions(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener { subscriptions ->
                    for (sc in subscriptions) {
                        val dt = sc.dataType!!
                        Log.i(TAG, "Active subscription for data type: ${dt.name}")
                    }

                    if (subscriptions.isEmpty()) {
                        Log.i(TAG, "No active subscriptions")
                    }
                }
        // [END list_current_subscriptions]
    }

    /**
     * Cancels the TYPE_CALORIES_EXPENDED subscription by calling unsubscribe on that [DataType].
     */
    private fun cancelSubscription() {
        val dataTypeStr = DataType.TYPE_CALORIES_EXPENDED.toString()
        Log.i(TAG, "Unsubscribing from data type: $dataTypeStr")

        // Invoke the Recording API to unsubscribe from the data type and specify a callback that
        // will check the result.
        // [START unsubscribe_from_datatype]
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unsubscribe(DataType.TYPE_CALORIES_EXPENDED)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully unsubscribed for data type: $dataTypeStr")
                }
                .addOnFailureListener {
                    // Subscription not removed
                    Log.i(TAG, "Failed to unsubscribe for data type: $dataTypeStr")
                }
        // [END unsubscribe_from_datatype]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_create_subs -> {
                checkPermissionsAndRun(::subscribe)
                true
            }
            R.id.action_cancel_subs -> {
                checkPermissionsAndRun(::cancelSubscription)
                true
            }
            R.id.action_dump_subs -> {
                checkPermissionsAndRun(::dumpSubscriptionsList)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Initializes a custom log class that outputs both to in-app targets and logcat.
     */
    private fun initializeLogging() { // Wraps Android's native log framework.
        val logWrapper = LogWrapper()
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper)
        // Filter strips out everything except the message text.
        val msgFilter = MessageOnlyLogFilter()
        logWrapper.next = msgFilter
        // On screen logging via a customized TextView.
        val logView = findViewById<View>(R.id.sample_logview) as LogView
        // Fixing this lint error adds logic without benefit.
        TextViewCompat.setTextAppearance(logView, R.style.Log)
        logView.setBackgroundColor(Color.WHITE)
        msgFilter.next = logView
        Log.i(TAG, "Ready")
    }

    private fun hasRuntimePermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions(signInAction: () -> Any) {
        val shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACTIVITY_RECOGNITION)

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        val requestCode = signInActionMap[signInAction]
        requestCode?.let {
            if (shouldProvideRationale) {
                Log.i(TAG, "Displaying permission rationale to provide additional context.")
                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        R.string.permission_rationale,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.ok) {
                            // Request permission
                            ActivityCompat.requestPermissions(this,
                                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                                    requestCode)
                        }
                        .show()
            } else {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        requestCode)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when {
            grantResults.isEmpty() -> {
                // If user interaction was interrupted, the permission request
                // is cancelled and you receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            }
            grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                // Permission was granted.
                val postPermissionAction = signInResponseMap[requestCode]
                postPermissionAction?.let {
                    fitSignIn(postPermissionAction)
                }
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

                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings) {
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts("package",
                                    BuildConfig.APPLICATION_ID, null)
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
            }
        }
    }
}