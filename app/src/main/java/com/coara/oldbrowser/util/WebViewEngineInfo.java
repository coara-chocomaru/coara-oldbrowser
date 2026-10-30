package com.coara.oldbrowser.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

public final class WebViewEngineInfo {
    private WebViewEngineInfo() {
    }

    public static String collect(Context context) {
        StringBuilder result = new StringBuilder();
        PackageInfo provider = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                provider = WebView.getCurrentWebViewPackage();
            } catch (Throwable ignored) {
            }
        }

        result.append("WebView / Chromium エンジン\n\n");
        result.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        if (provider != null) {
            result.append("プロバイダ: ").append(provider.packageName == null ? "不明" : provider.packageName).append('\n');
            result.append("エンジンバージョン: ")
                    .append(provider.versionName == null || provider.versionName.isEmpty() ? "不明" : provider.versionName)
                    .append('\n');
        } else {
            result.append("プロバイダ: 取得できません\n");
            result.append("設定 > 開発者向けオプション > WebView 実装 を確認してください\n");
        }

        List<String> features = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            features.add("Safe Browsing");
            features.add("レンダラプロセス異常検知");
        }
        features.add("ドキュメント開始スクリプト(リクエスト書換方式)");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            features.add("強制ダークモード");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            features.add("アルゴリズムダークニング");
        }

        result.append("\n利用可能な機能\n");
        for (String feature : features) {
            result.append("• ").append(feature).append('\n');
        }
        result.append("\nこのアプリはAndroidのWebViewプロバイダが提供するChromiumエンジンを使用します。\n");
        result.append("Chromeアプリ本体やChrome専用サービスを内蔵するものではなく、利用できる機能は端末のWebViewバージョンに依存します。");
        return result.toString();
    }
}
