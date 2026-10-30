-keepattributes RuntimeVisibleAnnotations,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepclassmembers,allowoptimization class com.coara.oldbrowser.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers,allowoptimization class com.coara.oldbrowser.SecretActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers,allowoptimization class com.coara.oldbrowser.webview.BlobDownloadBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers,allowoptimization class com.coara.oldbrowser.plugin.PluginBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers,allowoptimization class com.coara.oldbrowser.webview.CookieGate$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
