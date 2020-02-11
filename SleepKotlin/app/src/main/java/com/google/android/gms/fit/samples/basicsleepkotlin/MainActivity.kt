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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.fit.samples.basicsleepkotlin

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
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Session
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.fitness.result.SessionReadResponse
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

const val TAG = "SleepKotlin"

// For the purposes of this sample, a hard-coded period of time is defined, covering the nights of
// sleep that will be written and read.
const val PERIOD_START_DATE_TIME = "2020-01-20T12:00:00Z"
const val PERIOD_END_DATE_TIME = "2020-01-27T12:00:00Z"

/**
 * Define actions that can be performed after a successful sign in to Fit.
 * One of these values is passed to the Fit sign-in, and returned in a successful callback, allowing
 * subsequent execution of the desired action.
 */
enum class FitActionRequestCode {
    INSERT_SLEEP_SESSIONS,
    READ_SLEEP_AGGREGATED_BY_DAY,
    READ_SLEEP_AGGREGATED_BY_WEEK,
    READ_SLEEP_SESSIONS
}

/**
 * Demonstrates reading and writing sleep data with the Fit API:
 *
 * - Writes granular sleep sessions, including light, deep, rem types of sleep,.
 * - Reads sleep sessions summarized by day and entire period via aggregation.
 *
 * For this example, seven nights of sleep data are written for a specific period of time, that
 * falls within the period PERIOD_START_DATE_TIME to PERIOD_END_DATE_TIME. This bounding period is
 * then used in querying the Fit API for sleep data in raw and aggregated forms.
 */
class MainActivity : AppCompatActivity() {
    private val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
            .build()

    private val runningQOrLater =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    // Some responses from the Fit API are not filtered by activity type. This set is used as a
    // filter to retain only those types relevant to sleep.
    private val sleepActivities = setOf(
            FitnessActivities.SLEEP,
            FitnessActivities.SLEEP_LIGHT,
            FitnessActivities.SLEEP_DEEP,
            FitnessActivities.SLEEP_REM,
            FitnessActivities.SLEEP_AWAKE
    )

    // Defines the start and end of the period of interest in this example.
    private val periodStartMillis = millisFromRfc339DateString(PERIOD_START_DATE_TIME)
    private val periodEndMillis = millisFromRfc339DateString(PERIOD_END_DATE_TIME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeCustomLogging()
        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.

        checkPermissionsAndRun(FitActionRequestCode.INSERT_SLEEP_SESSIONS)
    }

    /**
     * Creates a list of data sets, one for each night of sleep for a given week, for inserting data
     * into Google Fit. (DSL used to simplify code.)
     *
     * @return A list of sleep data sets, one data set per night of sleep.
     */
    private fun createSleepDataSets(): List<DataSet> {
        val dataSource = getSleepDataSource()

        // The DSL allows data sets to be defined concisely by specifying just a start date/time
        // then following with durations for each period of sleep type, to avoid the need for
        // repetitive {@code DataSet} and {@code DataPoint} builders.
        return dataSets(dataSource) {
            dataSet("2020-01-20T23:00:00Z") {
                // Monday
                light(60)
                deep(60)
                light(60)
                rem(60)
                deep(60)
                light(120)
            }
            dataSet("2020-01-21T22:30:00Z") {
                // Tuesday
                light(60)
                deep(60)
                light(30)
                awake(30)
                deep(60)
                rem(60)
                light(120)
            }
            dataSet("2020-01-22T22:00:00Z") {
                // Wednesday
                light(120)
                deep(60)
                rem(60)
                deep(120)
                light(120)
            }
            dataSet("2020-01-23T23:00:00Z") {
                // Thursday
                light(120)
                deep(60)
                awake(30)
                deep(120)
                light(120)
            }
            dataSet("2020-01-24T22:30:00Z") {
                // Friday
                light(60)
                deep(60)
                light(30)
                deep(60)
                rem(60)
                light(90)
            }
            dataSet("2020-01-25T23:00:00Z") {
                // Saturday
                light(60)
                deep(60)
                light(60)
                deep(60)
                rem(60)
                light(120)
            }
            dataSet("2020-01-26T22:30:00Z") {
                // Sunday
                light(60)
                deep(60)
                light(30)
                awake(30)
                deep(60)
                rem(60)
                light(120)
            }
        }
    }

    private fun insertSleepSessions() {
        val requests = createSleepSessionRequests()
        val client = Fitness.getSessionsClient(this, getGoogleAccount())
        for (request in requests) {
            client.insertSession(request)
                    .addOnSuccessListener {
                        val (start, end) = getSessionStartAndEnd(request.session)
                        Log.i(TAG, "Added sleep: $start - $end")
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Failed to insert session for ", it)
                        it.printStackTrace()
                    }
        }
    }

    /**
     * Transforms each data set into a request to create a sleep entry in Google Fit. Each request
     * consists of session metadata and the underlying granular sleep data.
     *
     * @return A list of session requests for the Fit API.
     */
    private fun createSleepSessionRequests(): List<SessionInsertRequest> {
        val dataSets = createSleepDataSets()

        return dataSets.map {
            val session = createSleepSession(it)
            val request = SessionInsertRequest.Builder()
            request.addDataSet(it)
            request.setSession(session)
            request.build()
        }
    }

    private fun getSleepDataSource(): DataSource {
        return DataSource.Builder()
                .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                .setType(DataSource.TYPE_RAW)
                .setStreamName("MySleepSource")
                .setAppPackageName(this)
                .build()
    }

    /**
     * Reads raw, unaggregated sessions from the Fit API. Note that the Sessions client allows
     * filtering on data type, but not on activity type. The response therefore requires subsequent
     * filtering to obtain just the sleep data.
     */
    private fun readSleepSessions() {
        val client = Fitness.getSessionsClient(this, getGoogleAccount())

        val sessionReadRequest = SessionReadRequest.Builder()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .readSessionsFromAllApps()
                .setTimeInterval(periodStartMillis, periodEndMillis, TimeUnit.MILLISECONDS)
                .build()

        client.readSession(sessionReadRequest)
                .addOnSuccessListener { dumpSleepSessions(it) }
                .addOnFailureListener { Log.e(TAG, "Unable to read sleep sessions", it) }
    }

    /**
     * Reads sessions but aggregates the data by activity segment. This means that each session
     * contains one entry for each of the observed sleep types: light, deep etc, and the total
     * duration of each.
     */
    private fun readSleepSummaryBySession() {
        val client = Fitness.getHistoryClient(this, getGoogleAccount())

        val dataReadRequest = DataReadRequest.Builder()
                .enableServerQueries()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .bucketBySession(1, TimeUnit.SECONDS)
                .setTimeRange(periodStartMillis, periodEndMillis, TimeUnit.MILLISECONDS)
                .build()

        client.readData(dataReadRequest)
                .addOnSuccessListener { dumpSleepSummaryBySession(it) }
                .addOnFailureListener { Log.e(TAG, "Unable to read sleep summary", it) }
    }

    /**
     * Reads sleep data, aggregating by activity segment, but not segmenting by session. Instead, by
     * bucketing by activity type, the aggregate over the whole time period is calculated for each
     * granular type of sleep (as all granular sleep is of data type TYPE_ACTIVITY_SEGMENT).
     */
    private fun readSleepSummaryBySleepType() {
        val client = Fitness.getHistoryClient(this, getGoogleAccount())

        val dataReadRequest = DataReadRequest.Builder()
                .enableServerQueries()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .bucketByActivityType(1, TimeUnit.SECONDS)
                .setTimeRange(periodStartMillis, periodEndMillis, TimeUnit.MILLISECONDS)
                .build()

        client.readData(dataReadRequest)
                .addOnSuccessListener { dumpSleepSummaryBySleepType(it) }
                .addOnFailureListener { Log.e(TAG, "Unable to read sleep summary", it) }
    }

    /**
     * Filters a response form the Sessions client to contain only sleep sessions, then writes to
     * the log.
     *
     * @param response Response from the Sessions client.
     */
    private fun dumpSleepSessions(response: SessionReadResponse) {
        Log.clear()
        val sessions = response.sessions.filter { it.activity == FitnessActivities.SLEEP }

        for (session in sessions) {
            dumpSleepSession(session, response.getDataSet(session))
        }
    }

    /**
     * Filters a response that has been bucketed by session, to just sleep-related activities, then
     * writes to the log.
     *
     * @param response Response from the History client bucketed by session.
     */
    private fun dumpSleepSummaryBySession(response: DataReadResponse) {
        Log.clear()

        for (bucket in response.buckets) {
            val session = bucket.session
            session?.let {
                if (session.activity == FitnessActivities.SLEEP) {
                    dumpSleepSessionMetadata(session)
                    dumpSleepDataSets(bucket.dataSets)
                }
            }
        }
    }

    /**
     * Filters a response that has been bucketed by activity type, to just sleep-related activities,
     * then writes to the log.
     *
     * @param response Response from the History client bucketed by activity.
     */
    private fun dumpSleepSummaryBySleepType(response: DataReadResponse) {
        Log.clear()
        Log.i(TAG, "Sleep summary for $PERIOD_START_DATE_TIME - $PERIOD_END_DATE_TIME")
        val dataSets = response.buckets
                .filter { sleepActivities.contains(it.activity) }
                .flatMap { it.dataSets }

        dumpSleepDataSets(dataSets)
    }

    private fun dumpSleepSession(session: Session, dataSets: List<DataSet>) {
        dumpSleepSessionMetadata(session)
        dumpSleepDataSets(dataSets)
    }

    private fun dumpSleepSessionMetadata(session: Session) {
        val (startDateTime, endDateTime) = getSessionStartAndEnd(session)
        val totalSleepForNight = calculateSessionDuration(session)
        Log.i(TAG, "$startDateTime to $endDateTime ($totalSleepForNight mins)")
    }

    private fun dumpSleepDataSets(dataSets: List<DataSet>) {
        for (dataSet in dataSets) {
            for (dataPoint in dataSet.dataPoints) {
                val activity = dataPoint.getValue(Field.FIELD_ACTIVITY).asActivity()
                // When a dataset has a data type of Activity Summary, then the aggregate duration
                // is available as a value. However, for non-aggregated requests, Activity Segment
                // does not have duration, which must therefore be calculated from start and end.
                val millis = when (dataPoint.dataType) {
                    DataType.AGGREGATE_ACTIVITY_SUMMARY -> dataPoint.getValue(Field.FIELD_DURATION).asInt().toLong()
                    else -> dataPoint.getEndTime(TimeUnit.MILLISECONDS) - dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                }
                val duration = TimeUnit.MILLISECONDS.toMinutes(millis)
                Log.i(TAG, "\t$activity: $duration (mins)")
            }
        }
    }

    /**
     * Calculates the duration of a session in minutes.
     *
     * @param session
     * @return The duration in minutes.
     */
    private fun calculateSessionDuration(session: Session): Long {
        val total = session.getEndTime(TimeUnit.MILLISECONDS) - session.getStartTime(TimeUnit.MILLISECONDS)
        return TimeUnit.MILLISECONDS.toMinutes(total)
    }

    private fun getSessionStartAndEnd(session: Session): Pair<String, String> {
        val dateFormat = DateFormat.getDateTimeInstance()
        val startDateTime = dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS))
        val endDateTime = dateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS))
        return startDateTime to endDateTime
    }

    private fun createSleepSession(dataSet: DataSet): Session {
        return Session.Builder()
                .setActivity(FitnessActivities.SLEEP)
                .setStartTime(dataSet.dataPoints.first().getStartTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)
                .setEndTime(dataSet.dataPoints.last().getEndTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)
                .setName("Sleep")
                .build()
    }

    private fun millisFromRfc339DateString(dateString: String) =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .parse(dateString).time

    private fun checkPermissionsAndRun(fitActionRequestCode: FitActionRequestCode) {
        if (permissionApproved()) {
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
        if (oAuthPermissionsApproved()) {
            performActionForRequestCode(requestCode)
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                        this,
                        it.ordinal,
                        getGoogleAccount(), fitnessOptions)
            }
        }
    }

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    /**
     * Gets a Google account for use in creating the Fitness client. This is achieved by either
     * using the last signed-in account, or if necessary, prompting the user to sign in.
     * `getAccountForExtension` is recommended over `getLastSignedInAccount` as the latter can
     * return `null` if there has been no sign in before.
     */
    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

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
            else -> oAuthErrorMsg(requestCode, resultCode)
        }
    }

    /**
     * Runs the desired method, based on the specified request code. The request code is typically
     * passed to the Fit sign-in flow, and returned with the success callback. This allows the
     * caller to specify which method, post-sign-in, should be called.
     *
     * @param requestCode The code corresponding to the action to perform.
     */
    private fun performActionForRequestCode(requestCode: FitActionRequestCode) = when (requestCode) {
        FitActionRequestCode.INSERT_SLEEP_SESSIONS -> insertSleepSessions()
        FitActionRequestCode.READ_SLEEP_AGGREGATED_BY_DAY -> readSleepSummaryBySession()
        FitActionRequestCode.READ_SLEEP_AGGREGATED_BY_WEEK -> readSleepSummaryBySleepType()
        FitActionRequestCode.READ_SLEEP_SESSIONS -> readSleepSessions()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.read_sleep_all -> {
                checkPermissionsAndRun(FitActionRequestCode.READ_SLEEP_SESSIONS)
                true
            }
            R.id.read_aggregated_by_day -> {
                checkPermissionsAndRun(FitActionRequestCode.READ_SLEEP_AGGREGATED_BY_DAY)
                true
            }
            R.id.read_aggregated_by_week -> {
                checkPermissionsAndRun(FitActionRequestCode.READ_SLEEP_AGGREGATED_BY_WEEK)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun permissionApproved(): Boolean {
        return if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            true
        }
    }

    private fun requestRuntimePermissions(requestCode: FitActionRequestCode) {
        val shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACTIVITY_RECOGNITION)

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        requestCode.let {
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
                                    requestCode.ordinal)
                        }
                        .show()
            } else {
                Log.i(TAG, "Requesting permission")
                // Request permission. It's possible this can be auto answered if device policy
                // sets the permission in a given state or the user denied the permission
                // previously and checked "Never ask again".
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        requestCode.ordinal)
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

    /** Outputs both to in-app targets and logcat.  */
    private fun initializeCustomLogging() { // Wraps Android's native log framework.
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
}