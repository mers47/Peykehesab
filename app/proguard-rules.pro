# Room 3: required when minification/obfuscation is enabled so the generated
# database implementation remains discoverable at runtime.
-keep class * extends androidx.room3.RoomDatabase { <init>(); }

# Keep source/line information for actionable crash diagnostics in private builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
