Fit sync sample
===============

The purpose of this sample is to show how to implement a periodic upload of data
to Google Fit.

Pre-requisites
--------------

- Android API Level >= 14
- Android Build Tools v29
- Android Support Repository
- Register a Google Project with an Android client per getting started instructions
  http://developers.google.com/fit/android/get-started

Background
----------

Many fitness apps have the requirement to sync data to Google Fit on a regular
basis (e.g. daily). This sample demonstrates how to achieve this using
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager).

The frequency of synchronizing to Google Fit is set by `PERIODIC_SYNC_INTERVAL_SECONDS` in `FitSyncWorker`.

The sample also demonstrates:

-   Allowing for user-initiated immediate syncs
-   Handling transient errors with the upload, and retrying
-   Recording failure and using [notifications](https://developer.android.com/guide/topics/ui/notifiers/notifications)
    to alert the user.
-   Turning off the synchronisation after retries are exhausted and the
    sync process ultimately fails.

In this sample, a mock database `MockStepsDatabase` is used as the source of data to
be uploaded to Fit, which serves as a placeholder for a real local or remote
repository.

Support
-------

Use the following channels for support:

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/fit-samples

Patches are encouraged, and may be submitted according to the instructions in CONTRIB.md.