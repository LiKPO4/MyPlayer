# TASK_STATE

## 当前目标

- 继续 Flutter 版本播放器开发。
- 手机已通过无线 ADB 连接（魅族 22），可直接安装 release 包做真机验证。
- 后续打包优先使用 release 包，避免 debug 包体积过大。
- 项目已初始化 Git，并推送到公开仓库 `https://github.com/LiKPO4/MyPlayer`。
- 在线更新已接入 GitHub Releases，当前发布版本为 `1.0.11+12`。

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
- 版本 `v1.0.10+11` 已发布：`https://github.com/LiKPO4/MyPlayer/releases/tag/v1.0.10%2B11`（含扩展长度 mdat 修复 + v14 边界算法 + 长按"在文件管理器中显示"）。
- 修复魅族上"在文件管理器中显示"打开出 `.flymeSafeBox`：`revealInFileManager(...)` 改为两级目录打开——先把 SAF 目录 URI 经 `documentIdToPath(...)`（`raw:`/`primary:`/SD 卡卷）转真实路径用 `file://` 目录 Intent 打开（国产文件管理器支持最稳），被拦截（如 `FileUriExposedException`）或不可用时回退原 SAF `vnd.android.document/directory` 打开，最后才兜底打开视频本身。
- 实测确认魅族上 `.flymeSafeBox` 是 Flyme 文件管理器对"打开目录" Intent 的统一映射（`file://` 与 SAF 两种方式均无效），属 ROM 机制问题，App 无法绕过；改为打开目录的同时用 Snackbar 提示文件名与真实路径（`opened_folder:<path>` 返回协议），配合文件管理器搜索框快速定位。
- 机制深挖：`.flymeSafeBox` 根因是 SAF tree URI 授权只对我们 app 有效，Flyme 等文件管理器解析不了我们的 SAF document URI（无权限），回退到自身默认页。而 `file://` 真实路径不需要 SAF 授权，任何文件管理器都能读，但被 Android 7+ 的 `FileUriExposedException`（禁止跨应用传 file:// URI）拦截后我们回退到了 SAF。修复：新增 `openFolderWithFileScheme(...)`，发送前临时放宽 `StrictMode.VmPolicy` 放行 file:// 检查（finally 恢复），依次尝试 `resource/folder`（国产文件管理器通用目录 MIME）与 `vnd.android.document/directory`，任一被接受即成功打开目录。
- 真机实测 file:// 目录打开在 Flyme 上仍被解析成 `.flymeSafeBox`（系统"打开方式"选择器的目标文件即保险箱），目录方案在 Flyme 上彻底无效。最终改为：长按"在文件管理器中显示"对**视频文件本身**弹 `Intent.createChooser`"选择打开方式"（data 指向具体文件而非目录，MIME `*/*`，带 `FLAG_GRANT_READ_URI_PERMISSION`），由用户从列表中选文件管理器/应用打开该文件；删除已否决的目录打开死代码（`folderDocumentUri`/`openFolderWithFileScheme`/`documentIdToPath` 等）。
- 播放页竖滑切换改为原生纵向 `ViewPager2`：视频页面跟随手指整页移动并吸附，保留横向进度、点按/双击和长按倍速；使用 3 个 ExoPlayer 槽预加载当前页与相邻页。
- 修复原生竖滑时卡顿和黑屏：`PlayerView` 从默认 `SurfaceView` 切换为参与页面合成的 `TextureView`，开启重置时保留画面；解码器解绑、复用和相邻页准备延后到 `ViewPager2` 完全停止后执行，避免在 settling 动画中断开画面。
- 修复"点按隐藏菜单"在原生竖滑下失效：Flutter 侧 `onTap` 被平台视图拦截，改为原生 `PlayerBridge` 内 `GestureDetector`（挂 ViewPager2 内 RecyclerView）识别 `onSingleTapConfirmed`，经新增 `myplayer/player_ui_events` EventChannel 推 `toggle_controls` 给 Flutter 切换控制层；AndroidView 传入 `TapGestureRecognizer`/`VerticalDragGestureRecognizer` 保证手势竞技正确。
- 修复 App 强制开启系统"自动旋转"：根因是播放页退出时 `SystemChrome.setPreferredOrientations(DeviceOrientation.values)` 会向系统请求全方向传感器旋转。Manifest 增加 `android:screenOrientation="portrait"` 锁默认竖屏；播放页 dispose/竖屏切换固定为 `[portraitUp]`，横屏只用 `landscapeLeft`，不再出现全方向请求。
- 接通视频真实标题提取：标题存在明文 `moov` 的 `©nam`/`titl` 原子里（加密工具只 XOR 文件前缀）。`EncryptedVideoScanner.enrichTitles(...)` 扫描完成后对 `displayName == fileName` 的视频流式定位明文 moov（复用 `findEncryptedMoovBoxStart`）并提取标题（复用 `extractTitleFromMoov`），成功后更新浏览列表、扫描缓存记录、快照，推 `title` 事件给 Flutter；`PlayerBridge.updateTitle(...)` 同步刷新播放页列表与当前页标题（不重建播放器）。提取失败（moov 密文/无标题）保持文件名，下次扫描再试。
- 设置页去掉"清除随机记录"/"检查更新"的误导性右侧箭头；清除随机记录增加二次确认弹窗。
- 新增"播放完成后"设置：停止/重播/下一个，默认下一个；存储在原生 SharedPreferences（`playback_end_action`），经 `setEndAction` 下发到播放器，`NativePlayerPlatformView.handlePlaybackEnded(...)` 按行为分支。
- 播放页左右各 20% 区域恢复竖滑调节：左侧亮度、右侧音量，走原生通道（`setScreenBrightness` 仅作用于本页窗口不改系统设置，`setMusicVolume` 用 AudioManager），带百分比指示器，退出播放页自动恢复跟随系统亮度；中间区域保持原生竖滑换视频。
- 播放页单击响应提速：删除 Flutter 外层 `onDoubleTap`（它是 Flutter 手势竞技 300ms 延迟的来源），双击改由原生 `GestureDetector` 识别（`onDoubleTap` 先复原菜单再发 `double_tap_playpause`），单击改为 `onSingleTapUp` 立即生效，去掉两级 300ms 等待。
- 设置页新增"默认播放模式"（顺序/随机，默认顺序）：存原生 SharedPreferences（`default_shuffle`），`_playVideo` 未显式传 shuffle 时取该设置；"随机播放"按钮仍强制随机不受影响。
- 优化双击暂停时菜单闪现：改为"菜单可见→抬指立即隐藏（`tap_up`）；菜单不可见→双击确认窗口后才呼出（`tap_confirmed`）"。双击暂停全程菜单不闪现，隐藏仍即时；并用 `_tapHideAt` 时间戳配对 450ms 内的 confirmed，修复"单击隐藏后菜单又被弹回"的问题。
- 修复样例 `4822326c-6349-4794-ac47-08cd888d01d3`（91MB）打开黑屏：该文件 `moov` 长达 1.4MB，尾部跨过加密工具固定 XOR 的 1MB 边界转为明文，`findEncryptedPrefixEnd` 把首个全明文顶层 box（1.34MB 处 free）误判为 XOR 结束点，播放时把 [1MB,1.34MB) 的明文 moov 尾部再 XOR，ffmpeg 实证 `missing mandatory atoms`。新增 `refineBoundaryForOversizedBox(...)`：边界 >1MB 时探测 [1MB, 边界) 明文窗口的 box 类型 ASCII 命中或样本表高 0x00 率（密文随机区约 0.4%），命中则修正为 1MB；按 1MB 解密 ffmpeg 全量解码 0 错误。播放边界缓存升级到 `v15`。
- 补接黑屏修复的播放路径：已扫描视频的 scan 缓存仍存旧错误边界（缓存命中不重跑 inspect），播放时 `EncryptedVideoDataSource.resolveXorUntilOffset` 首播重验 `findEncryptedPrefixEnd` 后也必须过 `refineBoundaryForOversizedBox`，否则旧错误值会被重新写进 v15 缓存导致黑屏依旧。两处（Scanner 与 DataSource）现已一致。
- 浏览页新增全库搜索：顶栏搜索图标进入搜索视图，按视频标题/文件名实时过滤（大小写不敏感），结果显示所在文件夹（复用 `BrowserEntryTile` 新增的 `subtitleOverride`），点击播放整个结果列表，长按弹出视频菜单；重选目录（reset）时自动退出搜索，搜索模式下隐藏随机播放 FAB。
- 浏览页新增列表排序：顶栏排序图标弹出选择（按名称 A→Z / 按时间 新→旧 / 按大小 大→小，默认名称），文件夹仍排前面、组内按所选字段排序；偏好存原生 SharedPreferences（`sort_mode`，经 getSettings 恢复）；搜索结果的播放顺序跟随当前排序。
- 诊断样例 `59397aa5-8dc2-4b19-827b-090ef435e2ff`（拖动进度条卡住后自动跳下一条）：**结论是文件被截断，不是应用算错**。手机原件与桌面副本 sha256 一致（`af0df436…`），都是 1048576 字节；其 moov 声明总长 16739976、mdat 声明 15921770，实际只存在 mdat 前 230362 字节 → 缺 15691400 字节。Flyme 保险箱目录 166 个文件中仅此一个 1MiB，也无与缺失部分等大的文件。该文件自 2026-04-11 11:52 起就是这个大小，源文件本身残缺，需重新导出才能播放。真机 logcat 实证 `09:27:58 E/ExoPlayerImplInternal: Source error / Caused by: java.io.EOFException`（播放开始于 09:27:48）。v15 边界算法用 Python 复刻在三个文件上验证通过，文件完整时判定 K = 1MiB 正确。
- 新增「文件不完整」检测与提示（本轮改动）：
  - `EncryptedVideoFormat.findMissingTailBytes(...)`：走完顶层 box，某个 box 声明长度超出文件末尾即返回缺失字节数；结构完整或无法解析时返回 0（不判定）。不改动现有边界算法。
  - `EncryptedVideo` 增加 `incompleteBytes`（>0 截断 / 0 完整 / -1 未检测），同步 `toMap`、`fromMap`、扫描缓存记录与快照；扫描记录加 `recordVersion`（当前 2），老记录视为过期重检一次，已缓存视频也能拿到标记。
  - `EncryptedVideoScanner.inspectFileFast(...)` 计算并落库该标记；标题提取等 copy 操作天然保留。
  - `PlayerBridge.onPlayerError` 命中不完整视频时不再静默 `next()`，改为停住并上报 `incomplete_media:<缺失字节数>`；其它错误保持原行为。
  - Flutter 侧：列表副标题显示红色「· 文件不完整（缺 X）」；播放页收到上报后弹 Snackbar「视频文件不完整，缺少约 X 数据，无法继续播放」。新增顶层 `formatByteSize` 统一字节格式化。
  - 顺带修复既有问题：旧根目录镜像 `app/src/main/java/.../EncryptedVideoFormat.kt` 缺 v15 的 `refineBoundaryForOversizedBox` 与 `ByteArray.indexOf(ByteArray)`，导致 `:app:testDebugUnitTest` 编译失败；已补齐并同步到 v15。
- 版本 `v1.0.11+12` 已发布：`https://github.com/LiKPO4/MyPlayer/releases/tag/v1.0.11%2B12`（含竖滑原生信息流、标题提取、设置项、搜索排序 + 本轮「文件不完整」检测与提示）。首个 tag 触发的构建因 Flutter 最新 stable 把 Gradle 最低要求提到 8.14 而失败（项目仍是 8.10.2），已将 `release.yml` 固定 `flutter-version: "3.44.0"`（与本地开发/真机验证同一版本）后重指 tag 并发布成功；`gh api .../releases/latest` 返回 `v1.0.11+12`，应用内检查更新可拉到。

## 未完成

- 真机验证清单（由用户执行）：
  1. 标题提取：列表/播放页显示 moov 真实标题（moov 密文的文件仍显示文件名属预期）；
  2. 竖滑手势：中间竖滑换视频、左/右 20% 竖滑调亮度/音量且指示器正常、退出播放页亮度恢复系统默认；
  3. 单击响应应接近即时；单击显示/隐藏菜单与双击暂停、长按倍速、横滑进度并存；
  4. 设置页：播放完成后三种行为生效（重点验证"停止"和"重播"）、清除随机记录有确认弹窗、按钮无误导箭头、默认播放模式设为"随机"后点视频播放应随机往后播且可随时在播放页切回顺序；
  5. 系统"自动旋转"开关保持用户关闭状态。

## 阻塞

- 当前无阻塞；无线调试端口/IP 会漂移，需通过 `adb mdns services` 重新发现（2026-09-01 最新 192.168.0.103:35703）。

## 关键文件

- `lib/main.dart`
- `android/app/src/main/kotlin/com/lijialin/myplayer/MainActivity.kt`
- `android/app/src/main/kotlin/com/lijialin/myplayer/PlayerBridge.kt`
- `android/app/src/main/res/layout/video_pager_page.xml`
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
- `flutter build apk --release --no-pub --no-tree-shake-icons`：通过，桌面 APK 已覆盖到 `C:\Users\Administrator\Desktop\MyPlayer-release.apk`（含扩展长度 mdat 修复 + v14 边界算法 + 长按"在文件管理器中显示"）。
- GitHub Actions 发布构建（run 31233691191）：成功，Release `v1.0.10+11` 已发布并附带 `MyPlayer-v1.0.10+11.apk`（49.8MB），线上更新可拉取。
- `flutter build apk --release --no-pub --no-tree-shake-icons`：通过，桌面 APK 已覆盖到 `C:\Users\Administrator\Desktop\MyPlayer-release.apk`（含 file:// 目录打开修复，版本号仍为 `1.0.10+11`）。
- 竖滑切换版本：`flutter analyze`、`flutter test`、release APK 构建通过，并已覆盖安装到魅族 22。
- 真机手势验证：上滑从 `0014e4d6…` 切到 `00591fd4…`，下滑切回；横滑仍停留在同一视频并把进度从约 `0:02` 调到 `0:31`。
- 竖滑黑屏/卡顿修复：`flutter analyze`、`flutter test`、`:app:compileReleaseKotlin`、release APK 构建通过，已安装到魅族 22；ADB 自动上滑后未见 `MediaCodec`/`Surface`/Flutter 错误，SurfaceFlinger 中已无 MyPlayer 的 `SurfaceView` 图层。流畅度和观感待用户手动复测。
- 点按隐藏菜单 + 屏幕旋转修复 + 标题提取接线：`flutter analyze`、`flutter test`、`:app:compileReleaseKotlin`、`:app:testDebugUnitTest` 全部通过；release APK 构建通过并经无线 ADB 覆盖安装到魅族 22（2026-08-29 19:41，`adb install -r` Success）。三项均未做真机验证。
- 五项体验优化（箭头/确认弹窗/播放完成后行为/两侧亮度音量/单击提速）：`flutter analyze`、`flutter test`、`:app:compileReleaseKotlin`、`:app:testDebugUnitTest` 全部通过；release APK 构建通过并经无线 ADB 覆盖安装到魅族 22（2026-08-29 20:0x，`adb install -r` Success）。未做真机验证。
- 双击菜单不闪现优化 + 默认播放模式设置：本地验证全通过，含双击优化的 APK 已覆盖安装到魅族 22（2026-08-30 08:55，`adb install -r` Success）。未做真机验证。
- 未验证：真机 `788ae0e2` 是否恢复播放、首播前几秒卡顿是否消失、魅族上"在文件管理器中显示"是否打开到正确目录（`file://` 目录 Intent 行为）；"自动选中文件"仍无可靠系统方案。
- 「文件不完整」检测：`:app:testDebugUnitTest` 通过（21 个测试，新增 4 个：mdat 声明超出文件、完整 box 链、结构无法解析不判定、64 位扩展长度 mdat）；`flutter analyze` 无问题；`flutter test` 全部通过；`:app:compileReleaseKotlin` 通过；release APK 构建通过（49.9MB）并已覆盖到桌面 `MyPlayer-release.apk`。
- 完整性算法离线复核：`build/boundary_analysis/completeness_probe.py` 复刻线上逻辑，`59397aa5`（手机原件）判定缺失 `15691400` 与手算一致，`5a0e1f87`/`b89e5eeb` 均判定 0（完整）。
- 根因真机实证：无线 ADB 连魅族 22（`192.168.0.103:32795`），拉取手机原件 sha256 与桌面副本一致；logcat 抓到 `java.io.EOFException`。
- 「文件不完整」提示真机验证通过（2026-09-15 20:0x，魅族 22，`adb install -r` Success 且签名一致未丢数据）：
  1. 扫描侧 `复用 0 项缓存`，确认 `recordVersion` 升级触发全量重检；保险箱目录识别出 166 个视频；
  2. 搜索 `59397aa5` 结果显示红色「位于 … · 文件不完整（缺 15.0 MB）」，数值与 `15691400` 字节吻合；
  3. 播放该视频：播到 0:09 后停住，底部弹出「视频文件不完整，缺少约 15.0 MB 数据，无法继续播放」，**不再自动跳下一条**。

## 关键命令

```powershell
flutter analyze
flutter test
.\gradlew.bat :app:testDebugUnitTest
flutter build apk --release --no-pub --no-tree-shake-icons
Copy-Item -LiteralPath 'build\app\outputs\flutter-apk\app-release.apk' -Destination 'C:\Users\Administrator\Desktop\MyPlayer-release.apk' -Force
```

## 下一步

- **待办（重要）：升级 Android 构建工具链**。Flutter 已把最低要求提到 Gradle ≥ 8.14.0、AGP ≥ 8.11.1、Kotlin ≥ 2.2.20，项目当前是 Gradle 8.10.2 / AGP 8.7.0 / Kotlin 1.8.22。目前靠 `release.yml` 固定 Flutter 3.44.0 绕过；升级时需同步放开该固定，并重新构建 + 真机复测。
- `59397aa5` 这个视频要能正常播放，只能从原始来源重新拿到完整文件（缺 15.7MB），应用侧无法补救。
- 若用户需要，可用 `build/boundary_analysis/completeness_probe.py` 对保险箱里其余 165 个文件做一次完整性体检，排查是否还有别的残缺文件。
- 上表「未完成」里其余真机验证项（标题提取、手势、设置页等）仍待用户复测。
