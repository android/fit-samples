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

import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.Session
import java.text.DateFormat
import java.util.concurrent.TimeUnit

fun DataPoint.getStartTimeString(): String = DateFormat.getTimeInstance()
        .format(this.getStartTime(TimeUnit.MILLISECONDS))

fun DataPoint.getEndTimeString(): String = DateFormat.getTimeInstance()
        .format(this.getEndTime(TimeUnit.MILLISECONDS))

fun Session.getStartTimeString(): String = DateFormat.getTimeInstance()
        .format(this.getStartTime(TimeUnit.MILLISECONDS))

fun Session.getEndTimeString(): String = DateFormat.getTimeInstance()
        .format(this.getEndTime(TimeUnit.MILLISECONDS))