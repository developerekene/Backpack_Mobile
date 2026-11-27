# Add project specific ProGuard rules here.
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

# Firebase & Play Services
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# App Models & Firestore Data Classes
-keep class com.example.data.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# WebView JavaScript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve line numbers and source file names for Google Play Console ANR and crash analysis
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
