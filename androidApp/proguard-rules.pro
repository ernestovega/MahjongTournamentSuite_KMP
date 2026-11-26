# R8 rules for the release build. R8 removes unused code and resources and optimizes the rest.
#
# R8 obfuscates the libraries: their classes and members get short names, which makes the app smaller and lighter
# in memory. The mapping file (build/outputs/mapping/release/mapping.txt) turns them back; it travels inside the bundle
# and the Play deploy also uploads it (scripts/play.py), so Play shows crashes and ANRs with the real names.
#
# The code of the app keeps its names. The app reads class names at run time: the names of the routes are the screen
# names of the analytics and the keys of the saved screens. The reports of the Errors screen also stay readable
# without the mapping file. R8 still removes and optimizes the unused code of the app.
-keepnames class com.etologic.mahjongtournamentsuite.** { *; }

# The name of an exception tells a network failure apart from other errors (RepositorySupport.isConnectionFailure),
# and it is the type of an error report. The exceptions of the libraries keep their names too.
-keepnames class * extends java.lang.Throwable

# Stack traces keep the line numbers, and the mapping file the rest.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization, Ktor, Coil and Firebase bring their own rules. These classes are referenced by Ktor and
# OkHttp but are not on Android; nothing calls them.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Play Console looks for the call to EdgeToEdge.enable (enableEdgeToEdge in every activity) to tell that the app
# handles edge to edge. The call and the types of its signature keep their names, so Play finds
# EdgeToEdge.enable$default(Landroidx/activity/ComponentActivity;Landroidx/activity/SystemBarStyle;...).
-keepnames class androidx.activity.ComponentActivity
-keepnames class androidx.activity.SystemBarStyle
-keep class androidx.activity.EdgeToEdge {
    public static void enable(...);
    public static void enable$default(...);
}
