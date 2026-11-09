# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.invictus.abhyas.**$$serializer { *; }
-keepclassmembers class com.invictus.abhyas.** { *** Companion; }
-keepclasseswithmembers class com.invictus.abhyas.** { kotlinx.serialization.KSerializer serializer(...); }

# PDF Studio: PdfBox-Android optional JPEG2000 decoder is not bundled.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Decoder$Options
-dontwarn com.gemalto.jp2.**
