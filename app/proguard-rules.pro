# ProGuard rules for Offline IME
-keep class com.offline.ime.** { *; }
-dontwarn com.offline.ime.**

# Keep SQLite
-keep class android.database.sqlite.** { *; }

# Keep TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
