/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.fit.samples.basichistorysessionskotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.TextViewCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fit.samples.common.logger.Log
import com.google.android.gms.fit.samples.common.logger.LogView
import com.google.android.gms.fit.samples.common.logger.LogWrapper
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Session
import com.google.android.gms.fitness.request.DataDeleteRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.result.SessionReadResponse
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

const val TAG = "BasicSessions"
const val SAMPLE_SESSION_NAME = "Afternoon run"

/**
 * This sample demonstrates how to use the Sessions API of the Google Fit platform to insert
 * sessions into the History API, query against existing data, and remove sessions. It also
 * demonstrates how to authenticate a user with Google Play Services and how to properly
 * represent data in a Session, as well as how to use ActivitySegments.
 */
class MainActivity : AppCompatActivity() {
    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
                .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
                .build()
    }

    // Two maps are used in conjunction with the OAuth flow. This is in order to identify what
    // function to execute once the flow has successfully completed.
    private val signInActionMap = mapOf<() -> Any, Int>(
            ::insertAndVerifySession to 1,
            ::deleteSession to 2
    )
    private val signInResponseMap = signInActionMap.entries.associate { (k, v) -> v to k }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging()

        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.

        checkPermissionsAndRun(::insertAndVerifySession)
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

    private fun insertAndVerifySession() = insertSession().continueWith { verifySession() }

    /**
     * Creates and executes a [SessionInsertRequest] using [  ] to insert a session.
     */
    private fun insertSession(): Task<Void> {
        //First, create a new session and an insertion request.
        val insertRequest = createSessionInsertRequest()

        // [START insert_session]
        // Then, invoke the Sessions API to insert the session and await the result,
        // which is possible here because of the AsyncTask. Always include a timeout when
        // calling await() to avoid hanging that can occur from the service being shutdown
        // because of low memory or other conditions.
        Log.i(TAG, "Inserting the session in the Sessions API")
        return Fitness.getSessionsClient(this, getGoogleAccount())
                .insertSession(insertRequest)
                .addOnSuccessListener {
                    // At this point, the session has been inserted and can be read.
                    Log.i(TAG, "Session insert was successful!")
                }
                .addOnFailureListener {
                    it.printStackTrace()
                    Log.i(TAG, "There was a problem inserting the session: $it")
                }
        // [END insert_session]
    }

    /**
     * Creates and executes a [SessionReadRequest] using [  ] to verify the insertion succeeded .
     */
    private fun verifySession(): Task<SessionReadResponse> {
        // Begin by creating the query.
        val readRequest = readFitnessSession()

        // [START read_session]
        // Invoke the Sessions API to fetch the session with the query and wait for the result
        // of the read request. Note: Fitness.SessionsApi.readSession() requires the
        // ACCESS_FINE_LOCATION permission.
        return Fitness.getSessionsClient(this, getGoogleAccount())
                .readSession(readRequest)
                .addOnSuccessListener { sessionReadResponse ->
                    // Get a list of the sessions that match the criteria to check the result.
                    val sessions = sessionReadResponse.sessions
                    Log.i(TAG, "Session read was successful. Number of returned sessions is: " + sessions.size)

                    for (session in sessions) {
                        // Process the session
                        dumpSession(session)

                        // Process the data sets for this session
                        val dataSets = sessionReadResponse.getDataSet(session)
                        for (dataSet in dataSets) {
                            dumpDataSet(dataSet)
                        }
                    }
                }
                .addOnFailureListener { Log.i(TAG, "Failed to read session") }
        // [END read_session]
    }

    private fun createSessionInsertRequest(): SessionInsertRequest {
        Log.i(TAG, "Creating a new session for an afternoon run")
        // Setting start and end times for our run.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val now = Date()
        cal.time = now
        // Set a range of the run, using a start time of 30 minutes before this moment,
        // with a 10-minute walk in the middle.
        val endTime = cal.timeInMillis
        cal.add(Calendar.MINUTE, -10)
        val endWalkTime = cal.timeInMillis
        cal.add(Calendar.MINUTE, -10)
        val startWalkTime = cal.timeInMillis
        cal.add(Calendar.MINUTE, -10)
        val startTime = cal.timeInMillis

        // Create a data source
        val speedDataSource = DataSource.Builder()
                .setAppPackageName(this.packageName)
                .setDataType(DataType.TYPE_SPEED)
                .setStreamName("$SAMPLE_SESSION_NAME-speed")
                .setType(DataSource.TYPE_RAW)
                .build()

        val runSpeedMps = 10f
        val walkSpeedMps = 3f

        val firstRunSpeed = DataPoint.builder(speedDataSource)
                .setTimeInterval(startTime, startWalkTime, TimeUnit.MILLISECONDS)
                .setField(Field.FIELD_SPEED, runSpeedMps)
                .build()

        val walkSpeed = DataPoint.builder(speedDataSource)
                .setTimeInterval(startWalkTime, endWalkTime, TimeUnit.MILLISECONDS)
                .setField(Field.FIELD_SPEED, walkSpeedMps)
                .build()

        val secondRunSpeed = DataPoint.builder(speedDataSource)
                .setTimeInterval(endWalkTime, endTime, TimeUnit.MILLISECONDS)
                .setField(Field.FIELD_SPEED, runSpeedMps)
                .build()

        // Create a data set of the run speeds to include in the session.
        val speedDataSet = DataSet.builder(speedDataSource)
                .addAll(listOf(firstRunSpeed, walkSpeed, secondRunSpeed))
                .build()


        // [START build_insert_session_request_with_activity_segments]
        // Create a second DataSet of ActivitySegments to indicate the runner took a 10-minute walk
        // in the middle of the run.
        val activitySegmentDataSource = DataSource.Builder()
                .setAppPackageName(this.packageName)
                .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                .setStreamName("$SAMPLE_SESSION_NAME-activity segments")
                .setType(DataSource.TYPE_RAW)
                .build()

        val firstRunningDp = DataPoint.builder(activitySegmentDataSource)
                .setTimeInterval(startTime, startWalkTime, TimeUnit.MILLISECONDS)
                .setActivityField(Field.FIELD_ACTIVITY, FitnessActivities.RUNNING)
                .build()

        val walkingDp = DataPoint.builder(activitySegmentDataSource)
                .setTimeInterval(startWalkTime, endWalkTime, TimeUnit.MILLISECONDS)
                .setActivityField(Field.FIELD_ACTIVITY, FitnessActivities.WALKING)
                .build()

        val secondRunningDp = DataPoint.builder(activitySegmentDataSource)
                .setTimeInterval(endWalkTime, endTime, TimeUnit.MILLISECONDS)
                .setActivityField(Field.FIELD_ACTIVITY, FitnessActivities.RUNNING)
                .build()
        val activitySegments = DataSet.builder(activitySegmentDataSource)
                .addAll(listOf(firstRunningDp, walkingDp, secondRunningDp))
                .build()


        // [START build_insert_session_request]
        // Create a session with metadata about the activity.
        val session = Session.Builder()
                .setName(SAMPLE_SESSION_NAME)
                .setDescription("Long run around Shoreline Park")
                .setIdentifier("UniqueIdentifierHere")
                .setActivity(FitnessActivities.RUNNING)
                .setStartTime(startTime, TimeUnit.MILLISECONDS)
                .setEndTime(endTime, TimeUnit.MILLISECONDS)
                .build()

        // Build a session insert request
        // [END build_insert_session_request]
        // [END build_insert_session_request_with_activity_segments]

        return SessionInsertRequest.Builder()
                .setSession(session)
                .addDataSet(speedDataSet)
                .addDataSet(activitySegments)
                .build()
    }

    /**
     * Returns a [SessionReadRequest] for all speed data in the past week.
     */
    private fun readFitnessSession(): SessionReadRequest {
        Log.i(TAG, "Reading History API results for session: $SAMPLE_SESSION_NAME")
        // Set a start and end time for our query, using a start time of 1 week before this moment.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val now = Date()
        cal.time = now
        val endTime = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -1)
        val startTime = cal.timeInMillis

        // Build a session read request
        // [START build_read_session_request]
        val sessionRequest = SessionReadRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_SPEED)
                .setSessionName(SAMPLE_SESSION_NAME)
                .build()
        // [END build_read_session_request]
        return sessionRequest
    }

    private fun dumpDataSet(dataSet: DataSet) {
        Log.i(TAG, "Data returned for Data type: ${dataSet.dataType.name}")

        for (dp in dataSet.dataPoints) {
            Log.i(TAG, "Data point:")
            Log.i(TAG, "\tType: ${dp.dataType.name}")
            Log.i(TAG, "\tStart: ${dp.getStartTimeString()}")
            Log.i(TAG, "\tEnd: ${dp.getEndTimeString()}")
            dp.dataType.fields.forEach {
                Log.i(TAG, "\tField: ${it.name} Value: ${dp.getValue(it)}")
            }
        }
    }

    private fun dumpSession(session: Session) {
        Log.i(TAG, "Data returned for Session: " + session.name
                + "\n\tDescription: " + session.description
                + "\n\tStart: " + session.getStartTimeString()
                + "\n\tEnd: " + session.getEndTimeString())
    }

    /**
     * Deletes the [DataSet] we inserted with our [Session] from the History API.
     * In this example, we delete all step count data for the past 24 hours. Note that this
     * deletion uses the History API, and not the Sessions API, since sessions are truly just time
     * intervals over a set of data, and the data is what we are interested in removing.
     */
    private fun deleteSession() {
        Log.i(TAG, "Deleting today's session data for speed")

        // Set a start and end time for our data, using a start time of 1 day before this moment.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val now = Date()
        cal.time = now
        val endTime = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = cal.timeInMillis

        // Create a delete request object, providing a data type and a time interval
        val request = DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_SPEED)
                .deleteAllSessions() // Or specify a particular session here
                .build()

        // Delete request using HistoryClient and specify listeners that will check the result.
        Fitness.getHistoryClient(this, getGoogleAccount())
                .deleteData(request)
                .addOnSuccessListener { Log.i(TAG, "Successfully deleted today's sessions") }
                .addOnFailureListener {
                    // The deletion will fail if the requesting app tries to delete data
                    // that it did not insert.
                    Log.i(TAG, "Failed to delete today's sessions")
                }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_session -> {
                checkPermissionsAndRun(::deleteSession)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Initializes a custom log class that outputs both to in-app targets and logcat.  */
    private fun initializeLogging() {
        // Wraps Android's native log framework.
        val logWrapper = LogWrapper()
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper)
        // Filter strips out everything except the message text.
        val msgFilter = MessageOnlyLogFilter()
        logWrapper.next = msgFilter
        // On screen logging via a customized TextView.
        val logView = findViewById<LogView>(R.id.sample_logview)

        TextViewCompat.setTextAppearance(logView, R.style.Log)

        logView.setBackgroundColor(Color.WHITE)
        msgFilter.next = logView
        Log.i(TAG, "Ready.")
    }

    /** Returns the current state of the permissions needed.  */
    private fun hasRuntimePermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions(signInAction: () -> Any) {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)

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
        Log.i(TAG, "onRequestPermissionResult")
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