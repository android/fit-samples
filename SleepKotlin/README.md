Android Fit sleep sample
========================

A simple example of how to read and write sleep data to the Google Fit API

- Android API Level >= 14
- Android Build Tools v29
- Android Support Repository
- Register a Google Project with an Android client per getting started instructions
  http://developers.google.com/fit/android/get-started

Overview
--------

Sleep can be written either as :

- "Granular" - Different levels of sleep represented: light, deep, REM, awake.
- "Non-granular" - just sleep, no differentiating levels.

This example demonstrates reading and writing granular sleep data. The app writes seven nights of
sleep data to Google Fit, then uses either the Sessions client or History client to read the data
back depending on the level of aggregation chosen from the menu options.

Granular sleep is represented in Fit in two parts:

- A **session** covering the entire period of sleep of activity type `SLEEP`.
- **Activity segments**, falling with the session, of any of the granular sleep types.

See:

https://developers.google.com/fit/scenarios/read-sleep-data
https://developers.google.com/fit/scenarios/write-sleep-data

for more details on reading and writing sleep data.

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

NOTE: You must register an Android client underneath a Google Project in order for the Google Fit
API to become available for your app. The process ensures your app has proper consent screen
information for users to accept, among other things required to access Google APIs.
See the instructions for more details: http://developers.google.com/fit/android/get-started

Support
-------

The most common problem using these samples are sign in errors. Users can experience
this after selecting a Google Account to connect to the Fit API. This can be
seen in logcat.

If you encounter this error, check the following steps:

1.  Follow the instructions at http://developers.google.com/fit/android/get-started for registering an Android client.
1.  Ensure that the [Fit API](https://console.developers.google.com/apis/api/fitness.googleapis.com/overview) is enabled for your Cloud project.
1.  Check your [credentials](https://console.developers.google.com/apis/api/fitness.googleapis.com/credentials) for your Cloud project:
    - Ensure that your **package name** for your credentials matches the sample.
    - Ensure the **package name** matches the `applicationId` in the `app/build.gradle` file.
    - Ensure the **Signing-certificate fingerprint** is entered correctly.

Use the following channels for support:

- Stack Overflow: http://stackoverflow.com/questions/tagged/android
