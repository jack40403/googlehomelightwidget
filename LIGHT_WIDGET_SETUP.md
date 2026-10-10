# Google Home 燈光旋鈕 Widget

這個資料夾以 Google 官方的 Android Home APIs Sample App 為基礎，加入了彩色燈的旋鈕控制介面與 Android 桌面 Widget。

## 已加入的功能

- 在支援完整色彩的燈具詳情頁顯示圓形亮度旋鈕。
- 拖曳結束後才送出亮度命令，避免每一個手勢座標都呼叫燈具。
- 提供暖白、紅、黃、綠、藍、紫六個色彩捷徑。
- 使用 Google Home `ExtendedColorControl` 直接傳送 HSV 色彩命令。
- 在桌面新增「房間燈光」Widget；點擊後會開啟 App 進入燈具控制流程。

## 建置前置條件

Google Home Home APIs 的 Android SDK 不是一般公開 Maven 相依套件。必須使用具 Home APIs 存取權的 Google Home Developer 帳號，並依官方文件設定 SDK 與 OAuth 用戶端。

在這台電腦上建立 `local.properties`，填入你的 OAuth Web Client ID：

```properties
sdk.dir=C\:\\Users\\Lithium\\AppData\\Local\\Android\\Sdk
WEB_CLIENT_ID_DEV=你的_CLIENT_ID.apps.googleusercontent.com
```

然後以 Android Studio 開啟此資料夾，依 Google Home Developer Console 的設定流程完成簽章、OAuth redirect URI 與測試帳號設定，再部署到 Android 10 以上、已登入 Google Home 的手機。

## 使用方式

1. 開啟 App，完成 Google Home 授權。
2. 在 Devices 選擇房間中的彩色燈。
3. 在燈具詳情頁拖曳亮度旋鈕，或點擊色彩捷徑。
4. 長按 Android 桌面，從 Widget 清單加入「房間燈光」。

目前 Widget 用來快速開啟控制面板。Android 的 AppWidget 不適合連續拖曳控制，所以下一階段會增加 Widget 設定畫面，讓每個 Widget 綁定一盞燈，並提供固定亮度／色彩捷徑。
