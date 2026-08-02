# MyPlayer

Flutter + Android Media3 实现的本地加密视频播放器。

## 下载

最新版 APK 在 [GitHub Releases](https://github.com/LiKPO4/MyPlayer/releases/latest) 下载。

应用启动时会静默检查更新，也可以在“设置 > 检查更新”中手动检查。下载完成后会打开 Android 安装界面。

## 本地验证

```powershell
flutter analyze
flutter test
.\gradlew.bat :app:testDebugUnitTest
flutter build apk --release --no-pub --no-tree-shake-icons
```

## 发布

1. 更新 `pubspec.yaml` 中的版本号和构建号。
2. 提交并推送代码。
3. 创建与版本一致的 tag，例如 `v1.0.7+8`。
4. GitHub Actions 会使用仓库 Secrets 中的签名密钥构建 APK，并创建 GitHub Release。
