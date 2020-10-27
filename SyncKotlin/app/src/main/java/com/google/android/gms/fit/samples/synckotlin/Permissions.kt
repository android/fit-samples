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
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions

private val runningQOrLater =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

fun isActivityRecognitionPermissionApproved(context: Context): Boolean {
    return if (runningQOrLater) {
        PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
    } else {
        true
    }
}

fun isOAuthPermissionsApproved(context: Context, fitnessOptions: FitnessOptions) =
    GoogleSignIn.hasPermissions(getGoogleAccount(context, fitnessOptions), fitnessOptions)

/**
 * Gets a Google account for use in creating the Fitness client. This is achieved by either
 * using the last signed-in account, or if necessary, prompting the user to sign in.
 * `getAccountForExtension` is recommended over `getLastSignedInAccount` as the latter can
 * return `null` if there has been no sign in before.
 */
fun getGoogleAccount(context: Context, fitnessOptions: FitnessOptions) =
    GoogleSignIn.getAccountForExtension(context, fitnessOptions)