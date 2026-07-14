# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.store.cti.**$$serializer { *; }
-keepclassmembers class com.store.cti.** { *** Companion; }
-keepclasseswithmembers class com.store.cti.** { kotlinx.serialization.KSerializer serializer(...); }

# Room / Hilt / Compose はライブラリ側の consumer rules で対応済み
