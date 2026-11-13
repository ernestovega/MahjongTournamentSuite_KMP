# R8 rules for the release build. R8 removes unused code and resources and optimizes the rest.
#
# The names of classes stay as they are (no obfuscation). The app reads class names at run time: the names of the
# routes are the screen names of the analytics and the keys of the saved screens, and the names of exceptions tell a
# network failure apart from other errors. Readable names also keep crash reports readable without a mapping file.
-dontobfuscate

# kotlinx.serialization, Ktor, Coil and Firebase bring their own rules. These classes are referenced by Ktor and
# OkHttp but are not on Android; nothing calls them.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
