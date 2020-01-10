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
package com.google.android.gms.fit.samples.basicsensorsapikotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.TimeUnit

const val TAG = "BasicSensorsApi"

/**
 * This sample demonstrates how to use the Sensors API of the Google Fit platform to find available
 * data sources and to register/unregister listeners to those sources. It also demonstrates how to
 * authenticate a user with Google Play Services.
 */
class MainActivity : AppCompatActivity() {
    private val fitnessOptions = FitnessOptions.builder().addDataType(DataType.TYPE_LOCATION_SAMPLE).build()

    // Two maps are used in conjunction with the OAuth flow. This is in order to identify what
    // function to execute once the flow has successfully completed.
    private val signInActionMap = mapOf<() -> Any, Int>(
            ::findFitnessDataSources to 1
    )
    private val signInResponseMap = signInActionMap.entries.associate { (k, v) -> v to k }

    // [START dataPointListener_variable_reference]
    // Need to hold a reference to this listener, as it's passed into the "unregister"
    // method in order to stop all sensors from sending data to this listener.
    private var dataPointListener: OnDataPointListener? = null

    // [END dataPointListener_variable_reference]

    // [START auth_oncreate_setup]
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Put application specific code here.
        setContentView(R.layout.activity_main)
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging()
        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.
        checkPermissionsAndRun(::findFitnessDataSources)
    }

    private fun checkPermissionsAndRun(signInAction: () -> Any) {
        if (hasRuntimePermissions()) {
            fitSignIn(signInAction)
        } else {
            requestRuntimePermissions(signInAction)
        }
    }

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

    private fun hasOAuthPermissions() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    override fun onResume() {
        super.onResume()
        // This ensures that if the user denies the permissions then uses Settings to re-enable
        // them, the app will start working.
        fitSignIn(::findFitnessDataSources)
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
    // [END auth_oncreate_setup]

    /** Finds available data sources and attempts to register on a specific [DataType].  */
    private fun findFitnessDataSources() { // [START find_data_sources]
        // Note: Fitness.SensorsApi.findDataSources() requires the ACCESS_FINE_LOCATION permission.
        Fitness.getSensorsClient(this, getGoogleAccount())
                .findDataSources(
                        DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_LOCATION_SAMPLE)
                                .setDataSourceTypes(DataSource.TYPE_RAW)
                                .build())
                .addOnSuccessListener { dataSources ->
                    for (dataSource in dataSources) {
                        Log.i(TAG, "Data source found: $dataSource")
                        Log.i(TAG, "Data Source type: " + dataSource.dataType.name)
                        // Let's register a listener to receive Activity data!
                        if (dataSource.dataType == DataType.TYPE_LOCATION_SAMPLE && dataPointListener == null) {
                            Log.i(TAG, "Data source for LOCATION_SAMPLE found!  Registering.")
                            registerFitnessDataListener(dataSource, DataType.TYPE_LOCATION_SAMPLE)
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e(TAG, "failed", e) }
        // [END find_data_sources]
    }

    /**
     * Registers a listener with the Sensors API for the provided [DataSource] and [DataType] combo.
     */
    private fun registerFitnessDataListener(dataSource: DataSource, dataType: DataType) {
        // [START register_data_listener]
        dataPointListener = OnDataPointListener { dataPoint ->
            for (field in dataPoint.dataType.fields) {
                val value = dataPoint.getValue(field)
                Log.i(TAG, "Detected DataPoint field: ${field.name}")
                Log.i(TAG, "Detected DataPoint value: $value")
            }
        }
        Fitness.getSensorsClient(this, getGoogleAccount())
                .add(
                        SensorRequest.Builder()
                                .setDataSource(dataSource) // Optional but recommended for custom data sets.
                                .setDataType(dataType) // Can't be omitted.
                                .setSamplingRate(10, TimeUnit.SECONDS)
                                .build(),
                        dataPointListener)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "Listener registered!")
                    } else {
                        Log.e(TAG, "Listener not registered.", task.exception)
                    }
                }
        // [END register_data_listener]
    }

    /** Unregisters the listener with the Sensors API.  */
    private fun unregisterFitnessDataListener() {
        if (dataPointListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return
        }
        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        Fitness.getSensorsClient(this, getGoogleAccount())
                .remove(dataPointListener)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result!!) {
                        Log.i(TAG, "Listener was removed!")
                    } else {
                        Log.i(TAG, "Listener was not removed.")
                    }
                }
        // [END unregister_data_listener]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_unregister_listener) {
            unregisterFitnessDataListener()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Initializes a custom log class that outputs both to in-app targets and logcat.  */
    private fun initializeLogging() { // Wraps Android's native log framework.
        val logWrapper = LogWrapper()
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper)
        // Filter strips out everything except the message text.
        val msgFilter = MessageOnlyLogFilter()
        logWrapper.next = msgFilter
        // On screen logging via a customized TextView.
        val logView = findViewById<View>(R.id.sample_logview) as LogView
        TextViewCompat.setTextAppearance(logView, R.style.Log)
        logView.setBackgroundColor(Color.WHITE)
        msgFilter.next = logView
        Log.i(TAG, "Ready")
    }

    /** Returns the current state of the permissions needed.  */
    private fun hasRuntimePermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions(signInAction: () -> Any) {
        val shouldProvideRationale = ActivityCompat
                .shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)

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
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                    requestCode)
                        }
                        .show()
            } else {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        requestCode)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionResult" + grantResults.size)
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