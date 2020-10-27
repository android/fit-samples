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

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.tasks.Tasks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Worker class for performing upload of workout data to Google Fit.
 */
class FitSyncWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val MAX_RUN_ATTEMPTS = 5
        const val SYNC_JOB_NAME = "SyncJob"

        // Used to mark Work requests by how they were created: "Immediate" indicates to be run as
        // soon as possible, having been created by the user, as opposed to work requests created
        // as part of a repeated ongoing series of work requests.
        const val IMMEDIATE_TAG = "Immediate"
        val PERIODIC_SYNC_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(12)

        val fitnessOptions: FitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
            .build()
    }

    private val notifications = Notifications(appContext)

    private val dataSource = DataSource.Builder()
        .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
        .setAppPackageName(appContext)
        .setType(DataSource.TYPE_RAW)
        .build()

    private val sharedPreferences = appContext.getSharedPreferences(
        appContext.getString(R.string.preference_file_key),
        Context.MODE_PRIVATE
    )

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override suspend fun doWork(): Result {
        if (!isActivityRecognitionPermissionApproved(appContext)) {
            return buildRetryOrNotifyFailureResult(
                appContext.getString(R.string.error_android_permissions))
        } else if (!isOAuthPermissionsApproved(appContext, fitnessOptions)) {
            return buildRetryOrNotifyFailureResult(
                appContext.getString(R.string.error_oauth_permissions))
        }

        try {
            performSync()
        } catch (e: Exception) {
            return buildRetryOrNotifyFailureResult(e.toString())
        }

        sharedPreferences.edit(commit = true) {
            putString(
                appContext.getString(R.string.last_sync_date),
                getCurrentDateTimeString()
            )
        }

        /*
          Repeated work is achieved by using a one-time work request, where the final action is to
          enqueue another work request, as seen here, setting the appropriate initial delay.
        */
        enqueueRepeatedJob(appContext)
        return Result.success()
    }

    private fun buildRetryOrNotifyFailureResult(message: String): Result {
        return when (runAttemptCount) {
            MAX_RUN_ATTEMPTS -> {
                sharedPreferences.edit {
                    putBoolean(
                        appContext.getString(R.string.last_sync_failed),
                        true
                    )
                }
                notifications.showNotification(message)
                Result.failure(errorData(message))
            }
            else -> Result.retry()
        }
    }

    private fun performSync() {
        Log.i(TAG, "Performing sync")
        // Get the last sync time
        val lastSyncMillis =
            sharedPreferences.getLong(appContext.getString(R.string.last_sync_timestamp), 0)

        // Get any steps since lastSyncMillis. MockStepsDatabase is a class just for the purposes of
        // this sample, to serve as a placeholder for the storage an app might use, be it local or
        // in a app-specific cloud.
        val database = MockStepsDatabase()
        val stepsDelta = database.getStepsSinceLastSync(lastSyncMillis)

        if (isStopped) {
            return
        }

        if (stepsDelta.steps > 0) {
            val client =
                Fitness.getHistoryClient(appContext, getGoogleAccount(appContext, fitnessOptions))

            val dataSet = DataSet.builder(dataSource)
                .add(
                    DataPoint.builder(dataSource)
                        .setField(Field.FIELD_STEPS, stepsDelta.steps)
                        .setTimeInterval(
                            lastSyncMillis,
                            stepsDelta.timestamp,
                            TimeUnit.MILLISECONDS
                        )
                        .build()
                )
                .build()
            Log.i(TAG, "Inserting ${stepsDelta.steps} steps")

            if (isStopped) {
                return
            }
            val task = client.insertData(dataSet)
            Tasks.await(task)

            // update last sync time
            sharedPreferences.edit {
                putLong(
                    appContext.getString(R.string.last_sync_timestamp),
                    stepsDelta.timestamp
                )
            }
        } else {
            Log.i(TAG, "No steps to add since last sync.")
        }
    }

    private fun getCurrentDateTimeString() = dateFormatter.format(Date())

    private fun errorData(message: String) =
        Data.Builder().putString("error", message).build()
}

/**
 * Creates a unique entry in WorkManager to perform a sync to Google Fit, to be run on a repeating
 * basis.
 *
 * @param context The application context.
 */
fun enqueueRepeatedJob(context: Context) =
    enqueueSyncJob(context, FitSyncWorker.PERIODIC_SYNC_INTERVAL_SECONDS)

/**
 * Creates a unique entry in WorkManager to perform a sync to Google Fit, to be run as soon as
 * possible when the constraints are met.
 *
 * @param context The application context.
 */
fun enqueueImmediateJob(context: Context) = enqueueSyncJob(context)

/**
 * Creates a unique entry in WorkManager to perform a sync to Google Fit.
 *
 * @param context The application context.
 * @param delaySeconds The number of seconds to wait as an initial delay, before syncing.
 */
fun enqueueSyncJob(context: Context, delaySeconds: Long = 0) {
    val workManager = WorkManager.getInstance(context)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val requestBuilder = OneTimeWorkRequestBuilder<FitSyncWorker>()
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setConstraints(constraints)

    if (delaySeconds == 0L) {
        requestBuilder.addTag(FitSyncWorker.IMMEDIATE_TAG)
    }

    workManager.enqueueUniqueWork(
        FitSyncWorker.SYNC_JOB_NAME,
        ExistingWorkPolicy.REPLACE,
        requestBuilder.build()
    )
}