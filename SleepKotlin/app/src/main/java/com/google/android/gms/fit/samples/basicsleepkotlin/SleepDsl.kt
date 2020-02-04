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
package com.google.android.gms.fit.samples.basicsleepkotlin

import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.Field
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * These classes and functions define a DSL that enables more concise creation of sleep data sets,
 * allowing the code in {@code MainActivity} to be more easily readable.
 *
 * For an example of how to use it to define a data set, see the {@code MainActivity#createSleepDataSets}
 * method.
 */
fun dataSets(dataSource: DataSource, block: DataSetsBuilderKt.() -> Unit): List<DataSet> {
    val builder = DataSetsBuilderKt(dataSource)
    builder.apply(block)
    return builder.build()
}

class DataSetsBuilderKt(private val dataSource: DataSource) {
    private val dataSetList = mutableListOf<DataSet>()

    fun build(): List<DataSet> {
        return dataSetList.toList()
    }

    fun dataSet(startDateTime: String, block: DataSetBuilderKt.() -> Unit) {
        val builder = DataSetBuilderKt(dataSource, startDateTime)
        builder.apply(block)
        dataSetList.add(builder.build())
    }
}


class DataSetBuilderKt(val dataSource: DataSource, startDateTime: String) {
    val builder: DataSet.Builder = DataSet.builder(dataSource)
    var cursorMilliseconds = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .parse(startDateTime).time

    fun build(): DataSet {
        return builder.build()
    }
}

private fun DataSetBuilderKt.dataPoint(sleepType: String, minutes: Long) {
    val duration = TimeUnit.MINUTES.toMillis(minutes)
    val dataPoint = DataPoint.builder(dataSource)
            .setActivityField(Field.FIELD_ACTIVITY, sleepType)
            .setTimeInterval(cursorMilliseconds, cursorMilliseconds + duration, TimeUnit.MILLISECONDS)
            .build()
    builder.add(dataPoint)
    cursorMilliseconds += duration
}

fun DataSetBuilderKt.light(minutes: Long) = this.dataPoint(FitnessActivities.SLEEP_LIGHT, minutes)

fun DataSetBuilderKt.deep(minutes: Long) = this.dataPoint(FitnessActivities.SLEEP_DEEP, minutes)

fun DataSetBuilderKt.rem(minutes: Long) = this.dataPoint(FitnessActivities.SLEEP_REM, minutes)

fun DataSetBuilderKt.awake(minutes: Long) = this.dataPoint(FitnessActivities.SLEEP_AWAKE, minutes)