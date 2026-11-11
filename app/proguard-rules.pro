# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.monkaydee.tcgcatalogue.**$$serializer { *; }
-keepclassmembers class com.monkaydee.tcgcatalogue.** { *** Companion; }
-keepclasseswithmembers class com.monkaydee.tcgcatalogue.** { kotlinx.serialization.KSerializer serializer(...); }
