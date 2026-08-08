# TASK_STATE

## 当前目标

- 继续 Flutter 版本播放器开发。
- 手机当前没有 adb 连接，本阶段不处理 adb 安装/卸载。
- 后续打包优先使用 release 包，避免 debug 包体积过大。
- 项目已初始化 Git，并推送到公开仓库 `https://github.com/LiKPO4/MyPlayer`。
- 在线更新已接入 GitHub Releases，当前发布版本为 `1.0.9+10`。

## 已完成

- 修复加密 MP4 解密边界：`EncryptedVideoFormat.findEncryptedPrefixEnd(...)` 会扫描真实 XOR 结束位置，`transformReadBuffer(...)` 不再按固定 1MB 截断。
- 同步旧根目录 Android 测试目标，根目录 `:app:testDebugUnitTest` 可覆盖新解密边界逻辑。
- 随机播放增加已播记录：已播视频不再随机到，当前页面全播完会提示去设置清除记录。
- 设置入口可清除随机播放记录。
- 原生 `MainActivity.kt` 使用 `SharedPreferences` 保存 `random_played_uris`。
- 已生成桌面 release 包：`C:\Users\Administrator\Desktop\MyPlayer-release.apk`。
- 修复 release 包图标显示为错误方框：使用 `--no-tree-shake-icons` 重新构建，确保 APK 包含 `MaterialIcons-Regular.otf`。
- 顶部齿轮改为进入设置页；设置页包含默认播放速度、随机时是否包含子文件夹视频、清除随机记录。
- 默认播放速度和随机子文件夹开关由原生 `SharedPreferences` 持久化；随机子文件夹开启时，会从当前目录递归抽取子文件夹视频。
- 修复样例 `C:\Users\Administrator\Desktop\6ebbce0a-6dc2-415d-b40b-c0f6e5a2bcfa` 播放约 17 秒卡住：该文件在 `mdat` 内部约 `1050018` 字节处从 XOR 加密切回明文，旧逻辑误把整个 `mdat` 解 XOR。现在会在加密 `mdat` 内识别 H.264 样本明文切换点。
- 扫描缓存升级到 `scan-v9`、快照升级到 `snapshot-v3`，避免复用旧的 `xorUntilOffset`。
- 修复样例 `d6096573-668c-408e-9d8c-5c101e6605de` 在约 3 到 5 秒画面撕裂卡顿：该分片 MP4 的第二个 `mdat` 在偏移 `957582` 已从 XOR 切回明文，顶层明文 `moof` 到 `1580375` 才出现。上一版把中间约 623KB 明文再次 XOR，导致跨分片时画面损坏；现在分片 MP4 也会检查每个加密 `mdat` 内部的 H.264 明文切换点。
- 增强连续播放健壮性：`PlayerBridge` 增加 `onPlayerError`，遇到当前条目播放错误时尝试跳到下一条并重新 `prepare()`；手动上一条/下一条/恢复播放时，如果播放器处于 `STATE_IDLE`，也会重新 `prepare()`。
- 扫描缓存升级到 `scan-v11`、快照升级到 `snapshot-v5`，避免复用上一版错误的 `1580375` 分片边界。
- 增加在线更新：应用启动时静默检查，设置页可手动检查；发现新版本后使用 Android 系统下载器下载 APK，并打开系统安装界面。
- 增加 `.github/workflows/release.yml`：推送 `v*` tag 后自动使用 GitHub Secrets 中的同一签名密钥构建并发布 APK。
- 首个公开 Release `v1.0.7+8` 已发布：`https://github.com/LiKPO4/MyPlayer/releases/tag/v1.0.7%2B8`。
- 扫描缓存改用永久稳定键 `scan:` 和 `snapshot:`；升级后会自动迁移当前 `scan-v11`、`snapshot-v5` 数据，后续算法调整不得再通过修改整库缓存键淘汰缓存。
- 缓存保持版本 `v1.0.8+9` 已发布：`https://github.com/LiKPO4/MyPlayer/releases/tag/v1.0.8%2B9`。
- 深度修复部分视频在 3 到 10 秒撕裂卡顿：旧边界分析只扫描每个 `mdat` 前 8MB、只识别 H.264 NAL，并可能接受超出媒体数据范围的假长度；高码率或 HEVC 文件会因此漏判/误判明文切换点，导致播放器把正常字节再次 XOR。
- 边界分析改为低内存的全 `mdat` 流式扫描，支持 H.264/H.265，并按整个媒体负载校验 NAL 长度；新增超过 8MB 与 HEVC 切换点的回归测试。
- 不修改永久扫描缓存键。新增独立 `v12` 播放边界缓存：旧视频只在第一次播放时重新验证边界并持久化，避免整库重新扫描。
- 深度边界修复版本 `v1.0.9+10` 已发布：`https://github.com/LiKPO4/MyPlayer/releases/tag/v1.0.9%2B10`。
- 修复 v12 边界算法把密文区随机假明文 NAL 误判为切换点：样例 `6ebbce0a` 被误判到文件偏移 `2759`（真值 `1050018`），导致前 5-10 秒无法播放。`findPlainMediaTransition(...)` 改为 1KB 块明文/密文 NAL 密度统计 + 后缀差分和 argmax 定位切换块，再取块内首个明文 NAL；纯加密区后缀和为 0 不误判。播放边界缓存升级到 `v13`，受影响视频首次播放时自动重新验证。
- 新算法在三个真实样本上验证：`6ebbce0a` → `1050018`（与旧版已知正确值一致）、`5a0e1f87` → `1049778`、`b89e5eeb` → `1016686`；v12 对应错误值为 `2759`/`9124`/`1947`。
- 单元测试新增密文区假明文 NAL 干扰、全加密 mdat 无切换两个回归场景。
- 增加长按视频弹出菜单：「播放」和「在文件管理器中显示」。原生 `MainActivity.kt` 新增 `revealInFileManager`：优先用 `vnd.android.document/directory` 在系统文件管理器打开所在目录（根目录视频的 parentUri 为空时从 `directory_uri` tree URI 推导根 document URI），失败退化为 `video/*` 打开视频本身，再失败返回 `failed` 由 Dart 侧提示。
- 修复 `788ae0e2` 完全无法播放：根因是顶层 box 遍历守卫 `decodedSize < 8L` 把 64 位扩展长度标记（size==1）当非法长度直接返回 fileSize，扩展长度解析分支成为死代码，整个 32MB 被当密文 XOR。守卫改为 `decodedSize != 1L && decodedSize < 8L`，`findEncryptedPrefixEnd` 与 `findEncryptedMoovBoxStart` 同步修复。
- 边界算法升级到 v14：加密工具固定 XOR 文件前 1MB（ffmpeg 实证：四个真实文件 `5a0e1f87`/`b89e5eeb`/`6ebbce0a`/`788ae0e2` 按 K=1048576 解密均 0 错误）。`findPlainMediaTransition(...)` 改为单次流式累积差分扫描：先用差分证据验证 1MB 点（处于最低谷且其后转明文），命中直接返回 1MB——只需扫约 1.25MB，解决 v13 全 mdat 扫描导致的首播卡顿；证据不足退回 argmin 全扫（最低点后首个明文 NAL，差分回升 512 早退）。
- 单元测试新增三个场景：1MB 快路径命中、1MB 快路径被否决（真实切换点在其后 300KB）、64 位扩展长度 mdat 边界识别。

## 未完成

- 手机端安装和实机验证未完成。
- 仍需在真机验证：加密视频时长显示、进度拖动、随机播放记录、清除随机记录。

## 阻塞

- 手机未被 adb 识别；用户已说明手机没有 adb 连接，所以当前不处理 adb 相关内容。
- 若后续恢复 adb，旧包和新包签名不一致时需要先卸载 `com.lijialin.myplayer`，卸载会清除应用数据、目录授权和随机记录。

## 关键文件

- `lib/main.dart`
- `android/app/src/main/kotlin/com/lijialin/myplayer/MainActivity.kt`
- `android/app/src/main/kotlin/com/lijialin/myplayer/EncryptedVideoFormat.kt`
- `android/app/src/main/kotlin/com/lijialin/myplayer/EncryptedVideoScanner.kt`
- `android/app/src/main/kotlin/com/lijialin/myplayer/EncryptedVideoDataSource.kt`
- `app/src/main/java/com/lijialin/myplayer/EncryptedVideoFormat.kt`
- `app/src/main/java/com/lijialin/myplayer/EncryptedVideoScanner.kt`
- `app/src/test/java/com/lijialin/myplayer/EncryptedVideoFormatTest.kt`

## 最近验收

- `flutter analyze`：通过。
- `flutter test`：通过。
- `.\gradlew.bat :app:testDebugUnitTest`：通过（含 v14 边界算法 10 个回归测试）。
- v14 边界离线复核：`build/boundary_analysis/v14_replica.py` 复刻线上逻辑，四个真实文件均判定 K=1048576；`ffmpeg_check.py` 按 K=1048576 解密后 ffmpeg 解码均 0 错误（含此前完全无法播放的 `788ae0e2`）。
- `flutter build apk --release --no-pub --no-tree-shake-icons`：通过，桌面 APK 已覆盖到 `C:\Users\Administrator\Desktop\MyPlayer-release.apk`（含扩展长度 mdat 修复 + v14 边界算法 + 长按"在文件管理器中显示"，版本号仍为 `1.0.9+10`，发布前需先升版本号）。
- 未验证：真机 `788ae0e2` 是否恢复播放、首播前几秒卡顿是否消失、"在文件管理器中显示"行为。

## 关键命令

```powershell
flutter analyze
flutter test
.\gradlew.bat :app:testDebugUnitTest
flutter build apk --release --no-pub --no-tree-shake-icons
Copy-Item -LiteralPath 'build\app\outputs\flutter-apk\app-release.apk' -Destination 'C:\Users\Administrator\Desktop\MyPlayer-release.apk' -Force
```

## 下一步

- 安装桌面包 `MyPlayer-release.apk` 真机验证：`788ae0e2` 是否恢复播放、首播前几秒卡顿是否消失（v14 快路径只扫约 1.25MB）、长按菜单"在文件管理器中显示"。
- 真机通过后升 `pubspec.yaml` 版本号并发 `v1.0.10+11`（推送 tag 走 GitHub Actions 发布）。
- 本轮改动（v14 边界算法 + 扩展长度 box 守卫修复）尚未提交 git，待用户确认后提交。
