# CoaraBrowser — ライセンス情報

**CoaraBrowser 本体:** Apache License 2.0

## 第三者ライブラリ

本バージョンは、`app/build.gradle` に依存関係を一切宣言していません。AndroidX、Material Components、Markwon、ZXing、ML Kit、desugar_jdk_libs を含む第三者ライブラリは同梱していません。

アプリが利用するのは、Android プラットフォームが提供する標準 API（`android.*` / `java.*`）のみです。

## 自作コンポーネント

次の機能は本体のソースコードとして実装されています。

* QR コードの生成・読み取り（`com.coara.oldbrowser.qr`）
* Markdown 表示（`com.coara.oldbrowser.text`）
* リスト、カード、チップ、ボトムシートなどのウィジェット（`com.coara.oldbrowser.widget`）
* FileProvider 互換のコンテンツプロバイダ（`SafeFileProvider`）

## Android とその商標

Android SDK およびプラットフォームの利用条件は [Android SDK licensing information](https://developer.android.com/license) を参照してください。Android は Google LLC の商標です。

Copyright 2026 coara-chocomaru
