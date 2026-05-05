-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class ir.exchangerate.app.**$$serializer { *; }
-keepclassmembers class ir.exchangerate.app.** {
    *** Companion;
}
-keepclasseswithmembers class ir.exchangerate.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }
