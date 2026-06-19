-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-keepattributes !SourceFile,!LineNumberTable,!Signature,*Annotation*
-optimizations !code/allocation/variable
-mergeinterfacesaggressively
-dontusemixedcaseclassnames
-dontpreverify
-verbose
-renamesourcefileattribute SourceFile
-keep class com.termux.x11.** { *; }
-keepclassmembers class * extends android.preference.Preference {
    void onSetInitialValue(boolean, java.lang.Object);
}
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.IActivityManager
-dontwarn android.content.IIntentReceiver
-dontwarn android.content.IIntentReceiver$Stub
-dontwarn android.content.IIntentSender
-dontwarn android.content.pm.IPackageManager
