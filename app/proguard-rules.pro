# Room relies on generated implementations; keep entity and DAO annotations for reflection/tooling.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# OkHttp TLS and platform integrations.
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep the public checker contract used by tests and service composition.
-keep interface com.userhunter.app.checker.UsernameChecker { *; }
