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

/**
 * A placeholder class, to represent the local data storage for the fitness application. In this
 * small example, there is no actual storage, and a method is provided to simply return a number of
 * steps based on the length of time since the "database" was last queried.
 */
class MockStepsDatabase {
    fun getStepsSinceLastSync(syncTimeMillis: Long): StepsDelta {
        val now = System.currentTimeMillis()
        val steps = ((now - syncTimeMillis) / 3600).toInt()
        return StepsDelta(now, steps)
    }
}

data class StepsDelta(val timestamp: Long, val steps: Int)