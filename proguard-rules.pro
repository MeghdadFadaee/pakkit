# WorkManager uses class names to recreate persistent work after process death.
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
