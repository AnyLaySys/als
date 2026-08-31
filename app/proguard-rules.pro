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
-keepclassmembers class * extends android.preference.Preference {
    void onSetInitialValue(boolean, java.lang.Object);
}
-keep class sui.k.als.qemu.vm.VMNative { *; }
-keep class sui.k.als.qemu.vm.AGL { *; }
