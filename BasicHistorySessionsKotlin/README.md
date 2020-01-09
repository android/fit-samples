Android Fit History Api Sessions Sample
============

A simple example of how to use sessions with the History API on the Android Fit platform.


Pre-requisites
--------------

- Android API Level >= 14
- Android Build Tools v29
- Android Support Repository
- Register a Google Project with an Android client per getting started instructions
  http://developers.google.com/fit/android/get-started

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

The most common problem using these samples is a SIGN_IN_FAILED exception. Users can experience
this after selecting a Google Account to connect to the Fit API. If you see the following in
logcat output then make sure to register your Android app underneath a Google Project as outlined
in the instructions for using this sample at: http://developers.google.com/fit/android/get-started

`E/BasicSessions: There was an error signing into Fit.`

If you encounter this error, check the following steps:

1.  Follow the instructions at http://developers.google.com/fit/android/get-started for registering an Android client.
1.  Ensure that the [Fit API](https://console.developers.google.com/apis/api/fitness.googleapis.com/overview) is enabled for your Cloud project.
1.  Check your [credentials](https://console.developers.google.com/apis/api/fitness.googleapis.com/credentials) for your Cloud project:
    - Ensure that your **package name** for your credentials matches the sample.
    - Ensure the **package name** matches the `applicationId` in the `app/build.gradle` file.
    - Ensure the **Signing-certificate fingerprint** is entered correctly.

Use the following channels for support:

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/fit

Patches are encouraged, and may be submitted according to the instructions in CONTRIB.md.
