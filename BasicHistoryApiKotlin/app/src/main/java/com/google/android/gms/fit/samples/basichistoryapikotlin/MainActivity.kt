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
package com.google.android.gms.fit.samples.basichistoryapikotlin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fit.samples.common.logger.Log
import com.google.android.gms.fit.samples.common.logger.LogView
import com.google.android.gms.fit.samples.common.logger.LogWrapper
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataDeleteRequest
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.DataUpdateRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.Task
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

const val TAG = "BasicHistoryApi"

/**
 * This enum is used to define actions that can be performed after a successful sign in to Fit.
 * One of these values is passed to the Fit sign-in, and returned in a successful callback, allowing
 * subsequent execution of the desired action.
 */
enum class FitActionRequestCode {
    INSERT_AND_READ_DATA,
    UPDATE_AND_READ_DATA,
    DELETE_DATA
}

/**
 * This sample demonstrates how to use the History API of the Google Fit platform to insert data,
 * query against existing data, and remove data. It also demonstrates how to authenticate a user
 * with Google Play Services and how to properly represent data in a {@link DataSet}.
 */
class MainActivity : AppCompatActivity() {
    private val dateFormat = DateFormat.getDateInstance()
    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeLogging()

        fitSignIn(FitActionRequestCode.INSERT_AND_READ_DATA)
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
                        requestCode.ordinal,
                        getGoogleAccount(), fitnessOptions)
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
                postSignInAction.let {
                    performActionForRequestCode(postSignInAction)
                }
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
        FitActionRequestCode.INSERT_AND_READ_DATA -> insertAndReadData()
        FitActionRequestCode.UPDATE_AND_READ_DATA -> updateAndReadData()
        FitActionRequestCode.DELETE_DATA -> deleteData()
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

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    /**
     * Gets a Google account for use in creating the Fitness client. This is achieved by either
     * using the last signed-in account, or if necessary, prompting the user to sign in.
     * `getAccountForExtension` is recommended over `getLastSignedInAccount` as the latter can
     * return `null` if there has been no sign in before.
     */
    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    /**
     * Inserts and reads data by chaining {@link Task} from {@link #insertData()} and {@link
     * #readHistoryData()}.
     */
    private fun insertAndReadData() = insertData().continueWith { readHistoryData() }

    /** Creates a {@link DataSet} and inserts it into user's Google Fit history. */
    private fun insertData(): Task<Void> {
        // Create a new dataset and insertion request.
        val dataSet = insertFitnessData()

        // Then, invoke the History API to insert the data.
        Log.i(TAG, "Inserting the dataset in the History API.")
        return Fitness.getHistoryClient(this, getGoogleAccount())
                .insertData(dataSet)
                .addOnSuccessListener { Log.i(TAG, "Data insert was successful!") }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "There was a problem inserting the dataset.", exception)
                }
    }

    /**
     * Asynchronous task to read the history data. When the task succeeds, it will print out the
     * data.
     */
    private fun readHistoryData(): Task<DataReadResponse> {
        // Begin by creating the query.
        val readRequest = queryFitnessData()

        // Invoke the History API to fetch the data with the query
        return Fitness.getHistoryClient(this, getGoogleAccount())
                .readData(readRequest)
                .addOnSuccessListener { dataReadResponse ->
                    // For the sake of the sample, we'll print the data so we can see what we just
                    // added. In general, logging fitness information should be avoided for privacy
                    // reasons.
                    printData(dataReadResponse)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "There was a problem reading the data.", e)
                }
    }

    /**
     * Creates and returns a {@link DataSet} of step count data for insertion using the History API.
     */
    private fun insertFitnessData(): DataSet {
        Log.i(TAG, "Creating a new data insert request.")

        // [START build_insert_data_request]
        // Set a start and end time for our data, using a start time of 1 hour before this moment.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        val startTime = calendar.timeInMillis

        // Create a data source
        val dataSource = DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName("$TAG - step count")
                .setType(DataSource.TYPE_RAW)
                .build()

        // Create a data set
        val stepCountDelta = 950
        return DataSet.builder(dataSource)
                .add(DataPoint.builder(dataSource)
                        .setField(Field.FIELD_STEPS, stepCountDelta)
                        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build()
                ).build()
        // [END build_insert_data_request]
    }

    /** Returns a [DataReadRequest] for all step count changes in the past week.  */
    private fun queryFitnessData(): DataReadRequest {
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val startTime = calendar.timeInMillis

        Log.i(TAG, "Range Start: ${dateFormat.format(startTime)}")
        Log.i(TAG, "Range End: ${dateFormat.format(endTime)}")

        return DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                // bucketByTime allows for a time span, whereas bucketBySession would allow
                // bucketing by "sessions", which would need to be defined in code.
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()
    }

    /**
     * Logs a record of the query result. It's possible to get more constrained data sets by
     * specifying a data source or data type, but for demonstrative purposes here's how one would
     * dump all the data. In this sample, logging also prints to the device screen, so we can see
     * what the query returns, but your app should not log fitness information as a privacy
     * consideration. A better option would be to dump the data you receive to a local data
     * directory to avoid exposing it to other applications.
     */
    private fun printData(dataReadResult: DataReadResponse) {
        // [START parse_read_data_result]
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.buckets.isNotEmpty()) {
            Log.i(TAG, "Number of returned buckets of DataSets is: " + dataReadResult.buckets.size)
            for (bucket in dataReadResult.buckets) {
                bucket.dataSets.forEach { dumpDataSet(it) }
            }
        } else if (dataReadResult.dataSets.isNotEmpty()) {
            Log.i(TAG, "Number of returned DataSets is: " + dataReadResult.dataSets.size)
            dataReadResult.dataSets.forEach { dumpDataSet(it) }
        }
        // [END parse_read_data_result]
    }

    // [START parse_dataset]
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
    // [END parse_dataset]

    /**
     * Deletes a [DataSet] from the History API. In this example, we delete all step count data
     * for the past 24 hours.
     */
    private fun deleteData() {
        Log.i(TAG, "Deleting today's step count data.")

        // [START delete_dataset]
        // Set a start and end time for our data, using a start time of 1 day before this moment.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = calendar.timeInMillis

        //  Create a delete request object, providing a data type and a time interval
        val request = DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .build()

        // Invoke the History API with the HistoryClient object and delete request, and then
        // specify a callback that will check the result.
        Fitness.getHistoryClient(this, getGoogleAccount())
                .deleteData(request)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully deleted today's step count data.")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete today's step count data.", e)
                }
    }

    /**
     * Updates and reads data by chaining [Task] from [.updateData] and [ ][.readHistoryData].
     */
    private fun updateAndReadData() = updateData().continueWithTask { readHistoryData() }

    /**
     * Creates a [DataSet],then makes a [DataUpdateRequest] to update step data. Then
     * invokes the History API with the HistoryClient object and update request.
     */
    private fun updateData(): Task<Void> {
        // Create a new dataset and update request.
        val dataSet = updateFitnessData()
        val startTime = dataSet.dataPoints[0].getStartTime(TimeUnit.MILLISECONDS)
        val endTime = dataSet.dataPoints[0].getEndTime(TimeUnit.MILLISECONDS)
        // [START update_data_request]
        Log.i(TAG, "Updating the dataset in the History API.")

        val request = DataUpdateRequest.Builder()
                .setDataSet(dataSet)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

        // Invoke the History API to update data.
        return Fitness.getHistoryClient(this, getGoogleAccount())
                .updateData(request)
                .addOnSuccessListener { Log.i(TAG, "Data update was successful.") }
                .addOnFailureListener { e ->
                    Log.e(TAG, "There was a problem updating the dataset.", e)
                }
    }

    /** Creates and returns a {@link DataSet} of step count data to update. */
    private fun updateFitnessData(): DataSet {
        Log.i(TAG, "Creating a new data update request.")

        // [START build_update_data_request]
        // Set a start and end time for the data that fits within the time range
        // of the original insertion.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MINUTE, -50)
        val startTime = calendar.timeInMillis

        // Create a data source
        val dataSource = DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName("$TAG - step count")
                .setType(DataSource.TYPE_RAW)
                .build()

        // Create a data set
        val stepCountDelta = 1000
        // For each data point, specify a start time, end time, and the data value -- in this case,
        // the number of new steps.
        return DataSet.builder(dataSource)
                .add(DataPoint.builder(dataSource)
                        .setField(Field.FIELD_STEPS, stepCountDelta)
                        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build()
                ).build()
        // [END build_update_data_request]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_data -> {
                fitSignIn(FitActionRequestCode.DELETE_DATA)
                true
            }
            R.id.action_update_data -> {
                clearLogView()
                fitSignIn(FitActionRequestCode.UPDATE_AND_READ_DATA)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Clears all the logging message in the LogView.  */
    private fun clearLogView() {
        val logView = findViewById<LogView>(R.id.sample_logview)
        logView.text = ""
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
}
