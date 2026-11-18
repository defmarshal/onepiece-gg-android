# R8 rules for the release build.
#
# The debug build is never minified, so this file is only exercised by
# `assembleRelease`. Keep crash reports readable.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ------------------------------------------------
#
# The library ships consumer rules, but the app builds every request/response
# serializer explicitly (`X.serializer()` through the generated Companion) and
# parses with `AppJson.decodeFromString(...)`. Full-mode R8 can strip a
# `Companion` or a generated `$$serializer` that is only referenced that way,
# which turns into a runtime SerializationException rather than a compile error.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class gg.opgg.android.** {
    *** Companion;
}

-keepclasseswithmembers class gg.opgg.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class gg.opgg.android.**$$serializer { *; }

# ---- optional transports ------------------------------------------------
#
# OkHttp probes for Conscrypt / BouncyCastle / OpenJSSE at runtime. They are not
# on this classpath, and R8 fails the build on the missing references.

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**

# Ktor's debug logging backend is optional.
-dontwarn kotlinx.coroutines.debug.**

# Coil ships decoders for formats this app never requests.
-dontwarn coil.decode.**
