import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );
  runApp(const MyPlayerApp());
}

class MyPlayerApp extends StatelessWidget {
  const MyPlayerApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF19C294);
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'MyPlayer',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: seed,
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: Colors.white,
        textTheme: const TextTheme(
          headlineMedium: TextStyle(fontWeight: FontWeight.w800),
          titleLarge: TextStyle(fontWeight: FontWeight.w700),
          titleMedium: TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      home: const BrowserPage(),
    );
  }
}

class NativeBridge {
  static const MethodChannel _methods = MethodChannel('myplayer/native');
  static const EventChannel _scanEvents = EventChannel('myplayer/scan_events');
  static const EventChannel _playerEvents = EventChannel(
    'myplayer/player_events',
  );
  static const EventChannel _playerUiEvents = EventChannel(
    'myplayer/player_ui_events',
  );

  static Stream<Map<String, dynamic>> get scanEvents {
    return _scanEvents.receiveBroadcastStream().map((event) {
      return Map<String, dynamic>.from(event as Map);
    });
  }

  static Stream<PlayerPlaybackState> get playerEvents {
    return _playerEvents.receiveBroadcastStream().map((event) {
      return PlayerPlaybackState.fromMap(
        Map<String, dynamic>.from(event as Map),
      );
    });
  }

  static Stream<String> get playerUiEvents {
    return _playerUiEvents
        .receiveBroadcastStream()
        .where((event) => event is String)
        .cast<String>();
  }

  static Future<ScanResult?> chooseFolder() async {
    final result = await _methods.invokeMethod<Object?>('chooseFolder');
    return result == null
        ? null
        : ScanResult.fromMap(Map<String, dynamic>.from(result as Map));
  }

  static Future<ScanResult?> scanSavedFolder() async {
    final result = await _methods.invokeMethod<Object?>('scanSavedFolder');
    return result == null
        ? null
        : ScanResult.fromMap(Map<String, dynamic>.from(result as Map));
  }

  static Future<void> setPlaylist(List<EncryptedVideo> videos) {
    return _methods.invokeMethod<void>('setPlaylist', {
      'videos': videos.map((video) => video.toMap()).toList(),
    });
  }

  static Future<void> playAt(int index) {
    return _methods.invokeMethod<void>('playAt', {'index': index});
  }

  static Future<void> setShuffle(bool enabled) {
    return _methods.invokeMethod<void>('setShuffle', {'enabled': enabled});
  }

  static Future<void> playPause() {
    return _methods.invokeMethod<void>('playPause');
  }

  static Future<void> seekBy(Duration delta) {
    return _methods.invokeMethod<void>('seekBy', {
      'deltaMs': delta.inMilliseconds,
    });
  }

  static Future<void> seekTo(Duration position) {
    return _methods.invokeMethod<void>('seekTo', {
      'positionMs': position.inMilliseconds,
    });
  }

  static Future<void> next() {
    return _methods.invokeMethod<void>('next');
  }

  static Future<void> previous() {
    return _methods.invokeMethod<void>('previous');
  }

  static Future<void> setSpeed(double speed) {
    return _methods.invokeMethod<void>('setSpeed', {'speed': speed});
  }

  static Future<void> setEndAction(String action) {
    return _methods.invokeMethod<void>('setEndAction', {'action': action});
  }

  static Future<void> setDefaultShuffle(bool enabled) {
    return _methods.invokeMethod<void>('setDefaultShuffle', {
      'enabled': enabled,
    });
  }

  /// mode: 'name' | 'time' | 'size'
  static Future<void> setSortMode(String mode) {
    return _methods.invokeMethod<void>('setSortMode', {'mode': mode});
  }

  /// value 取 0..1；传 -1 恢复为跟随系统亮度（仅作用于本页面窗口）。
  static Future<void> setScreenBrightness(double value) {
    return _methods.invokeMethod<void>('setScreenBrightness', {
      'value': value,
    });
  }

  static Future<double> getScreenBrightness() async {
    final result = await _methods.invokeMethod<double>('getScreenBrightness');
    return (result as num?)?.toDouble() ?? 0.5;
  }

  /// value 取 0..1 的媒体音量比例。
  static Future<void> setMusicVolume(double value) {
    return _methods.invokeMethod<void>('setMusicVolume', {'value': value});
  }

  static Future<double> getMusicVolume() async {
    final result = await _methods.invokeMethod<double>('getMusicVolume');
    return (result as num?)?.toDouble() ?? 0.5;
  }

  static Future<Set<String>> getRandomPlayedUris() async {
    final result = await _methods.invokeMethod<List<Object?>>(
      'getRandomPlayedUris',
    );
    return result?.whereType<String>().toSet() ?? <String>{};
  }

  static Future<void> markRandomPlayed(String uri) {
    return _methods.invokeMethod<void>('markRandomPlayed', {'uri': uri});
  }

  static Future<void> clearRandomPlayed() {
    return _methods.invokeMethod<void>('clearRandomPlayed');
  }

  static Future<AppSettings> getSettings() async {
    final result = await _methods.invokeMethod<Object?>('getSettings');
    return result == null
        ? const AppSettings()
        : AppSettings.fromMap(Map<String, dynamic>.from(result as Map));
  }

  static Future<void> setDefaultPlaybackSpeed(double speed) {
    return _methods.invokeMethod<void>('setDefaultPlaybackSpeed', {
      'speed': speed,
    });
  }

  static Future<void> setRandomIncludeSubfolders(bool enabled) {
    return _methods.invokeMethod<void>('setRandomIncludeSubfolders', {
      'enabled': enabled,
    });
  }

  static Future<AppVersion> getAppVersion() async {
    final result = await _methods.invokeMethod<Object?>('getAppVersion');
    return AppVersion.fromMap(Map<String, dynamic>.from(result as Map));
  }

  static Future<String> downloadAndInstallUpdate(
    String url,
    String fileName,
  ) async {
    return await _methods.invokeMethod<String>('downloadAndInstallUpdate', {
          'url': url,
          'fileName': fileName,
        }) ??
        'failed';
  }

  static Future<String> revealInFileManager(BrowserEntry entry) async {
    final result = await _methods.invokeMethod<String>('revealInFileManager', {
      'uri': entry.uri,
      'parentUri': entry.parentUri,
    });
    return (result == null || result.isEmpty) ? 'failed' : result;
  }
}

class AppVersion {
  const AppVersion({required this.name, required this.code});

  final String name;
  final int code;

  factory AppVersion.fromMap(Map<String, dynamic> map) {
    return AppVersion(
      name: map['name'] as String? ?? '0.0.0',
      code: (map['code'] as num? ?? 0).toInt(),
    );
  }
}

class UpdateRelease {
  const UpdateRelease({
    required this.tag,
    required this.notes,
    required this.downloadUrl,
    required this.fileName,
  });

  final String tag;
  final String notes;
  final String downloadUrl;
  final String fileName;

  factory UpdateRelease.fromMap(Map<String, dynamic> map) {
    final assets = (map['assets'] as List? ?? const []).whereType<Map>();
    final apk = assets.cast<Map>().firstWhere(
      (asset) =>
          (asset['name'] as String? ?? '').toLowerCase().endsWith('.apk'),
      orElse: () => <String, dynamic>{},
    );
    return UpdateRelease(
      tag: map['tag_name'] as String? ?? '',
      notes: map['body'] as String? ?? '',
      downloadUrl: apk['browser_download_url'] as String? ?? '',
      fileName: apk['name'] as String? ?? 'MyPlayer-update.apk',
    );
  }
}

class UpdateService {
  static const _latestReleaseUrl =
      'https://api.github.com/repos/LiKPO4/MyPlayer/releases/latest';

  static Future<void> check(
    BuildContext context, {
    required bool silent,
  }) async {
    try {
      final current = await NativeBridge.getAppVersion();
      final release = await _fetchLatest(current);
      if (!context.mounted) return;
      if (!isNewer(release.tag, current)) {
        if (!silent) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('当前已是最新版本 ${current.name}')));
        }
        return;
      }
      if (release.downloadUrl.isEmpty) {
        if (!silent) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('最新版本没有可下载的 APK')));
        }
        return;
      }
      await _showUpdateDialog(context, current, release);
    } catch (_) {
      if (!silent && context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('检查更新失败，请稍后重试')));
      }
    }
  }

  static Future<UpdateRelease> _fetchLatest(AppVersion current) async {
    final client =
        HttpClient()..connectionTimeout = const Duration(seconds: 12);
    try {
      final request = await client.getUrl(Uri.parse(_latestReleaseUrl));
      request.headers.set(
        HttpHeaders.userAgentHeader,
        'MyPlayer/${current.name}',
      );
      request.headers.set(
        HttpHeaders.acceptHeader,
        'application/vnd.github+json',
      );
      final response = await request.close();
      if (response.statusCode != HttpStatus.ok) {
        throw HttpException('GitHub returned ${response.statusCode}');
      }
      final body = await utf8.decoder.bind(response).join();
      return UpdateRelease.fromMap(
        Map<String, dynamic>.from(jsonDecode(body) as Map),
      );
    } finally {
      client.close(force: true);
    }
  }

  static bool isNewer(String tag, AppVersion current) {
    final release = _parseVersion(tag);
    final installed = _parseVersion(current.name);
    if (release == null || installed == null) return false;
    for (var index = 0; index < 3; index++) {
      if (release[index] != installed[index]) {
        return release[index] > installed[index];
      }
    }
    final releaseCode = release[3];
    return releaseCode > 0 && releaseCode > current.code;
  }

  static List<int>? _parseVersion(String value) {
    final match = RegExp(
      r'^v?(\d+)\.(\d+)\.(\d+)(?:\+(\d+))?$',
    ).firstMatch(value.trim());
    if (match == null) return null;
    return List<int>.generate(
      4,
      (index) => int.parse(match.group(index + 1) ?? '0'),
    );
  }

  static Future<void> _showUpdateDialog(
    BuildContext context,
    AppVersion current,
    UpdateRelease release,
  ) async {
    final shouldDownload = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: Text('发现新版本 ${release.tag}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('当前版本 ${current.name}+${current.code}'),
                if (release.notes.trim().isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(release.notes.trim()),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('稍后'),
            ),
            FilledButton.icon(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              icon: const Icon(Icons.download_rounded),
              label: const Text('下载更新'),
            ),
          ],
        );
      },
    );
    if (shouldDownload != true || !context.mounted) return;
    final status = await NativeBridge.downloadAndInstallUpdate(
      release.downloadUrl,
      release.fileName,
    );
    if (!context.mounted) return;
    final message =
        status == 'permission_required'
            ? '请允许安装未知应用，然后再次检查更新'
            : status == 'downloading'
            ? '更新包开始下载，完成后将打开安装界面'
            : '无法开始下载更新';
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }
}

class AppSettings {
  const AppSettings({
    this.defaultPlaybackSpeed = 1.0,
    this.randomIncludeSubfolders = false,
    this.playbackEndAction = 'next',
    this.defaultShuffle = false,
    this.sortMode = 'name',
  });

  final double defaultPlaybackSpeed;
  final bool randomIncludeSubfolders;
  /// 'next' | 'replay' | 'stop'
  final String playbackEndAction;
  /// 默认播放模式：false 顺序、true 随机。
  final bool defaultShuffle;
  /// 列表排序：'name'（A→Z）/ 'time'（新→旧）/ 'size'（大→小）。
  final String sortMode;

  factory AppSettings.fromMap(Map<String, dynamic> map) {
    return AppSettings(
      defaultPlaybackSpeed:
          (map['defaultPlaybackSpeed'] as num? ?? 1.0).toDouble(),
      randomIncludeSubfolders: map['randomIncludeSubfolders'] as bool? ?? false,
      playbackEndAction: map['playbackEndAction'] as String? ?? 'next',
      defaultShuffle: map['defaultShuffle'] as bool? ?? false,
      sortMode: map['sortMode'] as String? ?? 'name',
    );
  }

  AppSettings copyWith({
    double? defaultPlaybackSpeed,
    bool? randomIncludeSubfolders,
    String? playbackEndAction,
    bool? defaultShuffle,
    String? sortMode,
  }) {
    return AppSettings(
      defaultPlaybackSpeed: defaultPlaybackSpeed ?? this.defaultPlaybackSpeed,
      randomIncludeSubfolders:
          randomIncludeSubfolders ?? this.randomIncludeSubfolders,
      playbackEndAction: playbackEndAction ?? this.playbackEndAction,
      defaultShuffle: defaultShuffle ?? this.defaultShuffle,
      sortMode: sortMode ?? this.sortMode,
    );
  }
}

class EncryptedVideo {
  const EncryptedVideo({
    required this.uri,
    required this.displayName,
    required this.fileName,
    required this.size,
    required this.lastModified,
    required this.xorUntilOffset,
    this.incompleteBytes = -1,
  });

  final String uri;
  final String displayName;
  final String fileName;
  final int size;
  final int lastModified;
  final int xorUntilOffset;

  /// 文件末尾缺失的字节数：>0 表示文件被截断，0 表示完整，-1 表示尚未检测。
  final int incompleteBytes;

  bool get isIncomplete => incompleteBytes > 0;

  factory EncryptedVideo.fromMap(Map<String, dynamic> map) {
    return EncryptedVideo(
      uri: map['uri'] as String,
      displayName: map['displayName'] as String,
      fileName: map['fileName'] as String? ?? map['displayName'] as String,
      size: (map['size'] as num).toInt(),
      lastModified: (map['lastModified'] as num).toInt(),
      xorUntilOffset: (map['xorUntilOffset'] as num).toInt(),
      incompleteBytes: (map['incompleteBytes'] as num? ?? -1).toInt(),
    );
  }

  Map<String, Object> toMap() {
    return {
      'uri': uri,
      'displayName': displayName,
      'fileName': fileName,
      'size': size,
      'lastModified': lastModified,
      'xorUntilOffset': xorUntilOffset,
      'incompleteBytes': incompleteBytes,
    };
  }
}

String formatByteSize(int size) {
  if (size <= 0) return '未知大小';
  const units = ['B', 'KB', 'MB', 'GB'];
  var value = size.toDouble();
  var unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return unit == 0
      ? '${value.toInt()} ${units[unit]}'
      : '${value.toStringAsFixed(1)} ${units[unit]}';
}

class BrowserEntry {
  const BrowserEntry({
    required this.type,
    required this.name,
    required this.uri,
    required this.parentUri,
    required this.size,
    required this.lastModified,
    required this.videoCount,
    this.video,
  });

  final String type;
  final String name;
  final String uri;
  final String? parentUri;
  final int size;
  final int lastModified;
  final int videoCount;
  final EncryptedVideo? video;

  bool get isFolder => type == 'folder';
  bool get isVideo => type == 'video' && video != null;

  factory BrowserEntry.fromMap(Map<String, dynamic> map) {
    final videoMap = map['video'];
    return BrowserEntry(
      type: map['type'] as String,
      name: map['name'] as String,
      uri: map['uri'] as String,
      parentUri: map['parentUri'] as String?,
      size: (map['size'] as num).toInt(),
      lastModified: (map['lastModified'] as num).toInt(),
      videoCount: (map['videoCount'] as num).toInt(),
      video:
          videoMap == null
              ? null
              : EncryptedVideo.fromMap(
                Map<String, dynamic>.from(videoMap as Map),
              ),
    );
  }
}

class ScanResult {
  const ScanResult({
    required this.entries,
    required this.videos,
    required this.skippedUnsupported,
    required this.failed,
  });

  final List<BrowserEntry> entries;
  final List<EncryptedVideo> videos;
  final int skippedUnsupported;
  final int failed;

  factory ScanResult.fromMap(Map<String, dynamic> map) {
    return ScanResult(
      entries:
          (map['entries'] as List? ?? const [])
              .map(
                (item) => BrowserEntry.fromMap(
                  Map<String, dynamic>.from(item as Map),
                ),
              )
              .toList(),
      videos:
          (map['videos'] as List? ?? const [])
              .map(
                (item) => EncryptedVideo.fromMap(
                  Map<String, dynamic>.from(item as Map),
                ),
              )
              .toList(),
      skippedUnsupported: (map['skippedUnsupported'] as num? ?? 0).toInt(),
      failed: (map['failed'] as num? ?? 0).toInt(),
    );
  }
}

class ScanProgress {
  const ScanProgress({
    required this.phase,
    required this.processed,
    required this.total,
    required this.found,
    required this.cached,
    required this.skipped,
    required this.failed,
  });

  final String phase;
  final int processed;
  final int total;
  final int found;
  final int cached;
  final int skipped;
  final int failed;

  double? get fraction => total <= 0 ? null : processed / total;

  factory ScanProgress.fromMap(Map<String, dynamic> map) {
    return ScanProgress(
      phase: map['phase'] as String? ?? 'idle',
      processed: (map['processed'] as num? ?? 0).toInt(),
      total: (map['total'] as num? ?? 0).toInt(),
      found: (map['found'] as num? ?? 0).toInt(),
      cached: (map['cached'] as num? ?? 0).toInt(),
      skipped: (map['skipped'] as num? ?? 0).toInt(),
      failed: (map['failed'] as num? ?? 0).toInt(),
    );
  }
}

class PlayerPlaybackState {
  const PlayerPlaybackState({
    required this.position,
    required this.duration,
    required this.buffered,
    required this.isPlaying,
    required this.speed,
    required this.currentIndex,
    required this.title,
  });

  final Duration position;
  final Duration duration;
  final Duration buffered;
  final bool isPlaying;
  final double speed;
  final int currentIndex;
  final String title;

  factory PlayerPlaybackState.fromMap(Map<String, dynamic> map) {
    return PlayerPlaybackState(
      position: Duration(
        milliseconds: (map['positionMs'] as num? ?? 0).toInt(),
      ),
      duration: Duration(
        milliseconds: (map['durationMs'] as num? ?? 0).toInt(),
      ),
      buffered: Duration(
        milliseconds: (map['bufferedMs'] as num? ?? 0).toInt(),
      ),
      isPlaying: map['isPlaying'] as bool? ?? false,
      speed: (map['speed'] as num? ?? 1.0).toDouble(),
      currentIndex: (map['currentIndex'] as num? ?? 0).toInt(),
      title: map['title'] as String? ?? '',
    );
  }
}

class BrowserPage extends StatefulWidget {
  const BrowserPage({super.key});

  @override
  State<BrowserPage> createState() => _BrowserPageState();
}

class _BrowserPageState extends State<BrowserPage> {
  final Random _random = Random();
  final TextEditingController _searchController = TextEditingController();
  StreamSubscription<Map<String, dynamic>>? _progressSubscription;

  List<BrowserEntry> _entries = const [];
  List<EncryptedVideo> _videos = const [];
  List<BrowserEntry> _folderStack = const [];
  AppSettings _settings = const AppSettings();
  ScanProgress? _progress;
  String _message = '请选择根目录';
  bool _scanning = false;
  bool _searching = false;
  String _searchQuery = '';

  @override
  void dispose() {
    _searchController.dispose();
    _progressSubscription?.cancel();
    super.dispose();
  }

  void _enterSearch() {
    setState(() => _searching = true);
  }

  void _exitSearch() {
    setState(() {
      _searching = false;
      _searchQuery = '';
      _searchController.clear();
    });
  }

  /// 排序选择：名称（A→Z）/ 时间（新→旧）/ 大小（大→小）。
  Future<void> _showSortSheet() async {
    final mode = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.white,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final option in const [
              ('name', '按名称', Icons.sort_by_alpha_rounded),
              ('time', '按时间', Icons.schedule_rounded),
              ('size', '按大小', Icons.sd_storage_rounded),
            ])
              ListTile(
                leading: Icon(option.$3),
                title: Text(option.$2),
                trailing:
                    _settings.sortMode == option.$1
                        ? const Icon(
                          Icons.check_rounded,
                          color: Color(0xFF1BB98B),
                        )
                        : null,
                onTap: () => Navigator.of(sheetContext).pop(option.$1),
              ),
          ],
        ),
      ),
    );
    if (mode == null) return;
    await NativeBridge.setSortMode(mode);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(sortMode: mode);
    });
  }

  /// 全库搜索：按视频标题（displayName）与文件名过滤，大小写不敏感。
  List<BrowserEntry> get _searchResults {
    final query = _searchQuery.trim().toLowerCase();
    if (query.isEmpty) return const [];
    return _sortEntries(
      _entries
          .where(
            (entry) =>
                entry.isVideo &&
                entry.video != null &&
                (entry.name.toLowerCase().contains(query) ||
                    entry.video!.fileName.toLowerCase().contains(query)),
          )
          .toList(),
    );
  }

  String? _folderNameOf(String? parentUri) {
    if (parentUri == null) return null;
    for (final entry in _entries) {
      if (entry.isFolder && entry.uri == parentUri) return entry.name;
    }
    return null;
  }

  String? get _currentParentUri =>
      _folderStack.isEmpty ? null : _folderStack.last.uri;

  String get _currentTitle =>
      _folderStack.isEmpty ? 'MyPlayer Pro' : _folderStack.last.name;

  List<BrowserEntry> get _currentEntries => _sortEntries(
    _entries.where((entry) => entry.parentUri == _currentParentUri).toList(),
  );

  List<EncryptedVideo> get _currentVideos =>
      _currentEntries
          .where((entry) => entry.isVideo)
          .map((entry) => entry.video!)
          .toList();

  List<EncryptedVideo> get _randomVideos {
    if (!_settings.randomIncludeSubfolders) return _currentVideos;
    if (_currentParentUri == null) return _videos;

    final folderUris = <String>{_currentParentUri!};
    var changed = true;
    while (changed) {
      changed = false;
      for (final entry in _entries) {
        if (entry.isFolder &&
            entry.parentUri != null &&
            folderUris.contains(entry.parentUri) &&
            folderUris.add(entry.uri)) {
          changed = true;
        }
      }
    }

    return _entries
        .where(
          (entry) =>
              entry.isVideo &&
              entry.parentUri != null &&
              folderUris.contains(entry.parentUri),
        )
        .map((entry) => entry.video!)
        .toList();
  }

  @override
  void initState() {
    super.initState();
    _progressSubscription = NativeBridge.scanEvents.listen((event) {
      if (!mounted) return;
      final phase = event['phase'] as String? ?? '';
      final progress = ScanProgress.fromMap(event);
      setState(() {
        _progress = progress;
        // title 事件发生在扫描完成后，属于增量标题刷新，不应把界面拉回“扫描中”。
        _scanning = progress.phase != 'done' && progress.phase != 'title';
        if (phase != 'title') {
          _message = _scanMessage(progress);
        }
        if (phase == 'reset') {
          _entries = const [];
          _videos = const [];
          _folderStack = const [];
          _searching = false;
          _searchQuery = '';
          _searchController.clear();
        } else if (phase == 'item') {
          final entryMap = event['entry'];
          if (entryMap != null) {
            _upsertEntry(
              BrowserEntry.fromMap(Map<String, dynamic>.from(entryMap as Map)),
            );
          }
        } else if (phase == 'video') {
          final videoMap = event['video'];
          if (videoMap != null) {
            _upsertVideo(
              EncryptedVideo.fromMap(
                Map<String, dynamic>.from(videoMap as Map),
              ),
            );
          }
        } else if (phase == 'title') {
          final videoMap = event['video'];
          if (videoMap != null) {
            _upsertVideo(
              EncryptedVideo.fromMap(
                Map<String, dynamic>.from(videoMap as Map),
              ),
            );
          }
          final entryMap = event['entry'];
          if (entryMap != null) {
            _upsertEntry(
              BrowserEntry.fromMap(Map<String, dynamic>.from(entryMap as Map)),
            );
          }
        }
      });
    });
    _restore();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      UpdateService.check(context, silent: true);
    });
  }

  Future<void> _restore() async {
    final settings = await NativeBridge.getSettings();
    if (mounted) {
      setState(() => _settings = settings);
    }
    final result = await NativeBridge.scanSavedFolder();
    if (!mounted) return;
    if (result == null) {
      setState(() => _message = '选择一个包含加密视频的根目录');
      return;
    }
    _applyResult(result);
  }

  Future<void> _chooseFolder() async {
    setState(() {
      _scanning = true;
      _message = '正在打开目录选择器...';
    });
    final result = await NativeBridge.chooseFolder();
    if (!mounted) return;
    if (result == null) {
      setState(() {
        _scanning = false;
        _message = '未选择目录';
      });
      return;
    }
    _applyResult(result);
  }

  Future<void> _refresh() async {
    setState(() {
      _scanning = true;
      _message = '正在刷新当前目录...';
    });
    final result = await NativeBridge.scanSavedFolder();
    if (!mounted) return;
    if (result == null) {
      setState(() {
        _scanning = false;
        _message = '请先选择根目录';
      });
      return;
    }
    _applyResult(result);
  }

  void _applyResult(ScanResult result) {
    setState(() {
      _entries = result.entries;
      _videos = result.videos;
      _folderStack =
          _folderStack
              .where(
                (folder) =>
                    result.entries.any((entry) => entry.uri == folder.uri),
              )
              .toList();
      _scanning = false;
      _message =
          '当前页面 ${result.entries.length} 项，${result.videos.length} 个可播放视频';
      if (result.skippedUnsupported > 0) {
        _message += '，跳过 ${result.skippedUnsupported} 个不支持文件';
      }
      if (result.failed > 0) {
        _message += '，${result.failed} 个读取失败';
      }
    });
  }

  void _upsertEntry(BrowserEntry entry) {
    final nextEntries = [..._entries];
    final existing = nextEntries.indexWhere((item) => item.uri == entry.uri);
    if (existing >= 0) {
      nextEntries[existing] = entry;
    } else {
      nextEntries.add(entry);
    }
    _entries = _sortEntries(nextEntries);
  }

  void _upsertVideo(EncryptedVideo video) {
    final nextVideos = [..._videos];
    final existing = nextVideos.indexWhere((item) => item.uri == video.uri);
    if (existing >= 0) {
      nextVideos[existing] = video;
    } else {
      nextVideos.add(video);
    }
    _videos = nextVideos;
  }

  List<BrowserEntry> _sortEntries(List<BrowserEntry> entries) {
    return entries..sort((a, b) {
      if (a.isFolder != b.isFolder) return a.isFolder ? -1 : 1;
      return switch (_settings.sortMode) {
        'time' => b.lastModified.compareTo(a.lastModified),
        'size' => b.size.compareTo(a.size),
        _ => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
      };
    });
  }

  Future<void> _playVideo(
    EncryptedVideo video, {
    /// 不传时用设置里的默认播放模式；随机播放入口显式传 true。
    bool? shuffle,
    List<EncryptedVideo>? playlistOverride,
  }) async {
    final effectiveShuffle = shuffle ?? _settings.defaultShuffle;
    final playlist = playlistOverride ?? _currentVideos;
    final index = playlist.indexWhere((item) => item.uri == video.uri);
    if (index < 0) return;
    await NativeBridge.setPlaylist(playlist);
    await NativeBridge.setShuffle(effectiveShuffle);
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder:
            (_) => PlayerPage(
              title: video.displayName,
              initialIndex: index,
              shuffle: effectiveShuffle,
              playlist: playlist,
              defaultPlaybackSpeed: _settings.defaultPlaybackSpeed,
              playbackEndAction: _settings.playbackEndAction,
            ),
      ),
    );
  }

  Future<void> _showVideoActions(BrowserEntry entry) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.white,
      builder:
          (sheetContext) => SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ListTile(
                  leading: const Icon(
                    Icons.play_circle_fill_rounded,
                    color: Color(0xFF1BB98B),
                  ),
                  title: const Text('播放'),
                  onTap: () => Navigator.of(sheetContext).pop('play'),
                ),
                ListTile(
                  leading: const Icon(
                    Icons.folder_open_rounded,
                    color: Color(0xFF1FC196),
                  ),
                  title: const Text('在文件管理器中显示'),
                  onTap: () => Navigator.of(sheetContext).pop('reveal'),
                ),
              ],
            ),
          ),
    );
    if (!mounted) return;
    if (action == 'play') {
      await _playVideo(entry.video!);
    } else if (action == 'reveal') {
      final result = await NativeBridge.revealInFileManager(entry);
      if (!mounted) return;
      if (result == 'failed') {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('没有找到可处理的应用')));
      } else if (result == 'opened_chooser') {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('请在列表中选择文件管理器打开：${entry.name}')));
      }
    }
  }

  Future<void> _randomPlay() async {
    final videos = _randomVideos;
    if (videos.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('当前页面没有可随机播放的视频')));
      return;
    }
    final playedUris = await NativeBridge.getRandomPlayedUris();
    final candidates =
        videos.where((video) => !playedUris.contains(video.uri)).toList();
    if (!mounted) return;
    if (candidates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('当前页面的视频已全部随机播放过，请到设置里清除随机记录')),
      );
      return;
    }
    final video = candidates[_random.nextInt(candidates.length)];
    await NativeBridge.markRandomPlayed(video.uri);
    await _playVideo(video, shuffle: true, playlistOverride: candidates);
  }

  Future<void> _openSettings() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => SettingsPage(initialSettings: _settings),
      ),
    );
    final next = await NativeBridge.getSettings();
    if (!mounted) return;
    setState(() => _settings = next);
  }

  void _enterFolder(BrowserEntry entry) {
    setState(() => _folderStack = [..._folderStack, entry]);
  }

  void _goUpFolder() {
    if (_folderStack.isEmpty) return;
    setState(
      () => _folderStack = _folderStack.sublist(0, _folderStack.length - 1),
    );
  }

  @override
  Widget build(BuildContext context) {
    final currentEntries = _currentEntries;
    return Scaffold(
      backgroundColor: Colors.white,
      body: Column(
        children: [
          _Header(
            title: _searching ? '搜索' : _currentTitle,
            canGoBack: !_searching && _folderStack.isNotEmpty,
            scanning: _scanning,
            progress: _progress,
            message: _searching ? '按视频标题或文件名搜索整个资料库' : _message,
            onBack: _goUpFolder,
            onChooseFolder: _chooseFolder,
            onRefresh: _refresh,
            onOpenSettings: _openSettings,
            onSearch: _enterSearch,
            onSort: _showSortSheet,
          ),
          Expanded(
            child: _searching
                ? _buildSearchView()
                : currentEntries.isEmpty
                ? EmptyState(onChooseFolder: _chooseFolder)
                : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(0, 14, 0, 104),
                  itemCount: currentEntries.length,
                  itemBuilder: (context, index) {
                    final entry = currentEntries[index];
                    return BrowserEntryTile(
                      entry: entry,
                      onTap: () {
                        if (entry.isVideo) {
                          _playVideo(entry.video!);
                        } else {
                          _enterFolder(entry);
                        }
                      },
                      onLongPress:
                          entry.isVideo
                              ? () => _showVideoActions(entry)
                              : null,
                    );
                  },
                ),
          ),
        ],
      ),
      floatingActionButton: _searching
          ? null
          : FloatingActionButton.small(
              heroTag: 'randomPlay',
              backgroundColor: const Color(0xFF1BB98B),
              foregroundColor: Colors.white,
              onPressed: _randomPlay,
              tooltip: '随机播放',
              child: const Icon(Icons.shuffle_rounded, size: 22),
            ),
    );
  }

  Widget _buildSearchView() {
    final results = _searchResults;
    final query = _searchQuery.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(6, 10, 14, 4),
          child: Row(
            children: [
              IconButton(
                tooltip: '退出搜索',
                onPressed: _exitSearch,
                color: const Color(0xFF303149),
                icon: const Icon(Icons.arrow_back_rounded),
              ),
              Expanded(
                child: TextField(
                  controller: _searchController,
                  autofocus: true,
                  onChanged: (value) => setState(() => _searchQuery = value),
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: '搜索全部视频',
                    isDense: true,
                    prefixIcon: const Icon(
                      Icons.search_rounded,
                      size: 22,
                      color: Color(0xFF92A0B8),
                    ),
                    suffixIcon:
                        _searchQuery.isEmpty
                            ? null
                            : IconButton(
                              icon: const Icon(Icons.close_rounded, size: 20),
                              onPressed: () {
                                _searchController.clear();
                                setState(() => _searchQuery = '');
                              },
                            ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(999),
                      borderSide: BorderSide.none,
                    ),
                    filled: true,
                    fillColor: const Color(0xFFF1F4F9),
                  ),
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 18, 6),
          child: Text(
            query.isEmpty
                ? '输入关键字开始搜索'
                : results.isEmpty
                ? '没有匹配“$query”的视频'
                : '找到 ${results.length} 个视频',
            style: const TextStyle(
              color: Color(0xFF8A96AD),
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        Expanded(
          child:
              results.isEmpty
                  ? const SizedBox.shrink()
                  : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(0, 4, 0, 24),
                    itemCount: results.length,
                    itemBuilder: (context, index) {
                      final entry = results[index];
                      final folderName = _folderNameOf(entry.parentUri);
                      return BrowserEntryTile(
                        entry: entry,
                        subtitleOverride: folderName == null
                            ? null
                            : '位于 $folderName',
                        onTap: () {
                          _playVideo(
                            entry.video!,
                            playlistOverride:
                                results.map((item) => item.video!).toList(),
                          );
                        },
                        onLongPress: () => _showVideoActions(entry),
                      );
                    },
                  ),
        ),
      ],
    );
  }

  String _scanMessage(ScanProgress progress) {
    if (progress.phase == 'reading') {
      return progress.processed > 0
          ? '正在读取目录：已发现 ${progress.processed} 项...'
          : '正在读取目录...';
    }
    if (progress.total <= 0) return '正在扫描目录...';
    final skippedText = progress.skipped > 0 ? '，跳过 ${progress.skipped}' : '';
    final failedText = progress.failed > 0 ? '，失败 ${progress.failed}' : '';
    return '扫描 ${progress.processed}/${progress.total}，视频 ${progress.found}，缓存 ${progress.cached}$skippedText$failedText';
  }
}

class _Header extends StatelessWidget {
  const _Header({
    required this.title,
    required this.canGoBack,
    required this.scanning,
    required this.progress,
    required this.message,
    required this.onBack,
    required this.onChooseFolder,
    required this.onRefresh,
    required this.onOpenSettings,
    required this.onSearch,
    required this.onSort,
  });

  final String title;
  final bool canGoBack;
  final bool scanning;
  final ScanProgress? progress;
  final String message;
  final VoidCallback onBack;
  final VoidCallback onChooseFolder;
  final VoidCallback onRefresh;
  final VoidCallback onOpenSettings;
  final VoidCallback onSearch;
  final VoidCallback onSort;

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.paddingOf(context).top;
    return Material(
      color: Colors.white,
      elevation: 0,
      child: Column(
        children: [
          Container(
            width: double.infinity,
            color: const Color(0xFF1FC196),
            padding: EdgeInsets.fromLTRB(18, topPadding + 12, 14, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    if (canGoBack)
                      IconButton(
                        tooltip: '返回上级',
                        color: Colors.white,
                        iconSize: 24,
                        onPressed: onBack,
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                    Expanded(
                      child: Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 0,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: '搜索',
                      color: Colors.white,
                      iconSize: 26,
                      onPressed: onSearch,
                      icon: const Icon(Icons.search_rounded),
                    ),
                    IconButton(
                      tooltip: '排序',
                      color: Colors.white,
                      iconSize: 26,
                      onPressed: onSort,
                      icon: const Icon(Icons.sort_rounded),
                    ),
                    IconButton(
                      tooltip: '设置',
                      color: Colors.white,
                      iconSize: 26,
                      onPressed: onOpenSettings,
                      icon: const Icon(Icons.settings_rounded),
                    ),
                    IconButton(
                      tooltip: '刷新',
                      color: Colors.white,
                      iconSize: 26,
                      onPressed: scanning ? null : onRefresh,
                      icon: const Icon(Icons.refresh_rounded),
                    ),
                    IconButton(
                      tooltip: '选择目录',
                      color: Colors.white,
                      iconSize: 26,
                      onPressed: scanning ? null : onChooseFolder,
                      icon: const Icon(Icons.folder_open_rounded),
                    ),
                  ],
                ),
                if (scanning) ...[
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(999),
                    child: LinearProgressIndicator(
                      minHeight: 4,
                      value: progress?.fraction,
                      backgroundColor: Colors.white.withValues(alpha: 0.28),
                      valueColor: const AlwaysStoppedAnimation<Color>(
                        Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    _progressText(),
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.92),
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ],
            ),
          ),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(18, 10, 18, 6),
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Color(0xFF8A96AD),
                fontSize: 12,
                height: 1.35,
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _progressText() {
    final value = progress;
    if (value == null) return '正在读取目录...';
    if (value.phase == 'reading') {
      return value.processed > 0
          ? '正在读取目录：${value.processed} 项...'
          : '正在读取目录...';
    }
    final skippedText = value.skipped > 0 ? '，跳过 ${value.skipped}' : '';
    final failedText = value.failed > 0 ? '，失败 ${value.failed}' : '';
    return '已扫描 ${value.processed}/${value.total}，发现 ${value.found} 个视频，复用 ${value.cached} 项缓存$skippedText$failedText';
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key, required this.initialSettings});

  final AppSettings initialSettings;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late AppSettings _settings = widget.initialSettings;
  bool _clearingRandomHistory = false;
  bool _checkingForUpdate = false;

  static const _speeds = [0.75, 1.0, 1.25, 1.5, 2.0, 3.0];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text('设置'),
        backgroundColor: const Color(0xFF1FC196),
        foregroundColor: Colors.white,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 10),
        children: [
          ListTile(
            leading: const Icon(Icons.speed_rounded),
            title: const Text('默认播放速度'),
            subtitle: Text(_formatSpeed(_settings.defaultPlaybackSpeed)),
            trailing: DropdownButton<double>(
              value: _settings.defaultPlaybackSpeed,
              underline: const SizedBox.shrink(),
              items:
                  _speeds.map((speed) {
                    return DropdownMenuItem<double>(
                      value: speed,
                      child: Text(_formatSpeed(speed)),
                    );
                  }).toList(),
              onChanged: (speed) {
                if (speed != null) _setDefaultPlaybackSpeed(speed);
              },
            ),
          ),
          ListTile(
            leading: const Icon(Icons.playlist_play_rounded),
            title: const Text('默认播放模式'),
            trailing: DropdownButton<bool>(
              value: _settings.defaultShuffle,
              underline: const SizedBox.shrink(),
              items: const [
                DropdownMenuItem<bool>(value: false, child: Text('顺序')),
                DropdownMenuItem<bool>(value: true, child: Text('随机')),
              ],
              onChanged: (shuffle) {
                if (shuffle != null) _setDefaultShuffle(shuffle);
              },
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.account_tree_rounded),
            title: const Text('随机子文件夹视频'),
            subtitle: const Text('随机播放时包含当前目录下的子文件夹视频'),
            value: _settings.randomIncludeSubfolders,
            onChanged: _setRandomIncludeSubfolders,
          ),
          ListTile(
            leading: const Icon(Icons.near_me_rounded),
            title: const Text('播放完成后'),
            subtitle: Text(_endActionLabel(_settings.playbackEndAction)),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: _showEndActionSheet,
          ),
          ListTile(
            leading: const Icon(Icons.cleaning_services_rounded),
            title: const Text('清除随机记录'),
            subtitle: const Text('清除后，已随机播放过的视频可再次被抽到'),
            trailing:
                _clearingRandomHistory
                    ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const SizedBox.shrink(),
            onTap: _clearingRandomHistory ? null : _clearRandomHistory,
          ),
          ListTile(
            leading: const Icon(Icons.system_update_rounded),
            title: const Text('检查更新'),
            subtitle: const Text('从 GitHub Releases 获取最新版本'),
            trailing:
                _checkingForUpdate
                    ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                    : const SizedBox.shrink(),
            onTap: _checkingForUpdate ? null : _checkForUpdate,
          ),
        ],
      ),
    );
  }

  Future<void> _setDefaultPlaybackSpeed(double speed) async {
    await NativeBridge.setDefaultPlaybackSpeed(speed);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(defaultPlaybackSpeed: speed);
    });
  }

  Future<void> _setDefaultShuffle(bool shuffle) async {
    await NativeBridge.setDefaultShuffle(shuffle);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(defaultShuffle: shuffle);
    });
  }

  Future<void> _setRandomIncludeSubfolders(bool enabled) async {
    await NativeBridge.setRandomIncludeSubfolders(enabled);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(randomIncludeSubfolders: enabled);
    });
  }

  Future<void> _showEndActionSheet() async {
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final entry in const [
              ('next', '播放下一个', '当前列表播完后自动切到下一个视频'),
              ('replay', '重新播放', '播完后从头再来一遍'),
              ('stop', '停止', '播完后停在最后一帧'),
            ])
              ListTile(
                leading: Icon(
                  switch (entry.$1) {
                    'replay' => Icons.replay_rounded,
                    'stop' => Icons.stop_rounded,
                    _ => Icons.skip_next_rounded,
                  },
                ),
                title: Text(entry.$2),
                subtitle: Text(entry.$3),
                trailing:
                    _settings.playbackEndAction == entry.$1
                        ? const Icon(
                          Icons.check_rounded,
                          color: Color(0xFF1BB98B),
                        )
                        : null,
                onTap: () => Navigator.of(sheetContext).pop(entry.$1),
              ),
          ],
        ),
      ),
    );
    if (action == null) return;
    await NativeBridge.setEndAction(action);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(playbackEndAction: action);
    });
  }

  String _endActionLabel(String action) {
    return switch (action) {
      'replay' => '重新播放',
      'stop' => '停止',
      _ => '播放下一个',
    };
  }

  Future<void> _clearRandomHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清除随机记录'),
        content: const Text('清除后，已随机播放过的视频可以再次被随机抽到。确定清除吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('清除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _clearingRandomHistory = true);
    await NativeBridge.clearRandomPlayed();
    if (!mounted) return;
    setState(() => _clearingRandomHistory = false);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('已清除随机播放记录')));
  }

  Future<void> _checkForUpdate() async {
    setState(() => _checkingForUpdate = true);
    await UpdateService.check(context, silent: false);
    if (!mounted) return;
    setState(() => _checkingForUpdate = false);
  }

  String _formatSpeed(double speed) {
    return '${speed.toStringAsFixed(speed == speed.roundToDouble() ? 0 : 1)}x';
  }
}

class BrowserEntryTile extends StatelessWidget {
  const BrowserEntryTile({
    super.key,
    required this.entry,
    required this.onTap,
    this.onLongPress,
    this.subtitleOverride,
  });

  final BrowserEntry entry;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;
  /// 覆盖默认的“大小/N 个视频”副标题（搜索结果里显示所在文件夹）。
  final String? subtitleOverride;

  @override
  Widget build(BuildContext context) {
    final isFolder = entry.isFolder;
    final missingBytes = entry.video?.incompleteBytes ?? 0;
    final isIncomplete = missingBytes > 0;
    final baseSubtitle =
        subtitleOverride ??
        (isFolder ? '${entry.videoCount} 个视频' : formatByteSize(entry.size));
    final subtitle =
        isIncomplete
            ? '$baseSubtitle · 文件不完整（缺 ${formatByteSize(missingBytes)}）'
            : baseSubtitle;
    return InkWell(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 7, 16, 7),
        child: Row(
          children: [
            FolderArtwork(isFolder: isFolder),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    entry.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color:
                          isFolder
                              ? const Color(0xFF303149)
                              : const Color(0xFF0B63D8),
                      fontSize: 17,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 0,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    subtitle,
                    style: TextStyle(
                      color:
                          isIncomplete
                              ? const Color(0xFFD14343)
                              : const Color(0xFF92A0B8),
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 0,
                    ),
                  ),
                ],
              ),
            ),
            if (entry.isVideo)
              const Icon(
                Icons.play_circle_fill_rounded,
                color: Color(0xFF1BB98B),
                size: 24,
              ),
          ],
        ),
      ),
    );
  }
}

class FolderArtwork extends StatelessWidget {
  const FolderArtwork({super.key, required this.isFolder});

  final bool isFolder;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 58,
      height: 43,
      child: CustomPaint(painter: FolderPainter(showPlay: !isFolder)),
    );
  }
}

class FolderPainter extends CustomPainter {
  const FolderPainter({required this.showPlay});

  final bool showPlay;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = const Color(0xFFE0E5EA);
    final tab =
        Path()
          ..moveTo(0, 10)
          ..quadraticBezierTo(0, 0, 10, 0)
          ..lineTo(size.width * 0.38, 0)
          ..quadraticBezierTo(size.width * 0.48, 0, size.width * 0.55, 8)
          ..quadraticBezierTo(size.width * 0.62, 14, size.width * 0.72, 14)
          ..lineTo(size.width - 8, 14)
          ..quadraticBezierTo(size.width, 14, size.width, 22)
          ..lineTo(size.width, size.height - 8)
          ..quadraticBezierTo(
            size.width,
            size.height,
            size.width - 8,
            size.height,
          )
          ..lineTo(8, size.height)
          ..quadraticBezierTo(0, size.height, 0, size.height - 8)
          ..close();
    canvas.drawPath(tab, paint);

    if (showPlay) {
      final iconPaint = Paint()..color = const Color(0xFFC6CDD5);
      final play =
          Path()
            ..moveTo(size.width * 0.43, size.height * 0.36)
            ..lineTo(size.width * 0.43, size.height * 0.68)
            ..lineTo(size.width * 0.68, size.height * 0.52)
            ..close();
      canvas.drawPath(play, iconPaint);
    }
  }

  @override
  bool shouldRepaint(covariant FolderPainter oldDelegate) =>
      oldDelegate.showPlay != showPlay;
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.onChooseFolder});

  final VoidCallback onChooseFolder;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.folder_open_rounded,
              size: 64,
              color: Color(0xFFCAD2DB),
            ),
            const SizedBox(height: 14),
            const Text(
              '选择根目录后显示文件和文件夹',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Color(0xFF303149),
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: onChooseFolder,
              icon: const Icon(Icons.folder_rounded),
              label: const Text('选择文件夹'),
            ),
          ],
        ),
      ),
    );
  }
}

class PlayerPage extends StatefulWidget {
  const PlayerPage({
    super.key,
    required this.title,
    required this.initialIndex,
    required this.shuffle,
    required this.playlist,
    required this.defaultPlaybackSpeed,
    required this.playbackEndAction,
  });

  final String title;
  final int initialIndex;
  final bool shuffle;
  final List<EncryptedVideo> playlist;
  final double defaultPlaybackSpeed;
  final String playbackEndAction;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  StreamSubscription<PlayerPlaybackState>? _playerSubscription;
  StreamSubscription<String>? _playerUiSubscription;
  bool _shuffle = false;
  bool _landscape = false;
  bool _portrait = false;
  bool _holdingSpeed = false;
  bool _controlsVisible = true;
  bool _isPlaying = false;
  String _title = '';
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  int _currentIndex = 0;
  Duration? _seekGestureStartPosition;
  Duration? _seekGesturePreviewPosition;
  double _seekGestureDelta = 0;
  double _playbackSpeed = 1.0;
  double _holdSpeed = 3.0;
  int? _lastMarkedRandomIndex;

  // 左右两侧竖滑调节亮度/音量的手势状态。
  double? _adjustStartDy;
  double? _adjustBaseValue;
  bool _adjustIsBrightness = false;
  double? _adjustIndicatorValue;
  Timer? _adjustIndicatorTimer;
  double _brightness = 1.0;
  double _volume = 0.5;

  // 最近一次"单击导致菜单隐藏"的时间，用于把 300ms 后到达的 tap_confirmed
  // 与那次单击配对，避免隐藏后又把菜单弹回（配对窗口只需覆盖确认延迟）。
  DateTime? _tapHideAt;

  /// 原生播放出错且原因是文件被截断时携带缺失字节数上报的前缀。
  static const String _incompleteMediaPrefix = 'incomplete_media:';

  @override
  void initState() {
    super.initState();
    _shuffle = widget.shuffle;
    _title = widget.title;
    _playbackSpeed = widget.defaultPlaybackSpeed;
    _playerSubscription = NativeBridge.playerEvents.listen((state) {
      if (!mounted) return;
      setState(() {
        _position = state.position;
        _duration = state.duration;
        _isPlaying = state.isPlaying;
        _currentIndex = state.currentIndex;
        if (state.title.isNotEmpty) _title = state.title;
      });
      _markRandomIndex(state.currentIndex);
    });
    _playerUiSubscription = NativeBridge.playerUiEvents.listen((event) {
      if (!mounted) return;
      // 菜单可见时抬指立即隐藏；不可见时等双击确认窗口过后再呼出，
      // 双击暂停全程菜单不闪现。tap_confirmed 若属于刚执行过隐藏的那次
      // 单击（tap_hide_at 配对），则不能把菜单重新弹回。
      if (event == 'tap_up') {
        if (_controlsVisible) {
          _tapHideAt = DateTime.now();
          _toggleControls();
        }
      } else if (event == 'tap_confirmed') {
        final hideAt = _tapHideAt;
        _tapHideAt = null;
        final sameTap =
            hideAt != null &&
            DateTime.now().difference(hideAt) <
                const Duration(milliseconds: 450);
        if (!_controlsVisible && !sameTap) _toggleControls();
      } else if (event == 'double_tap_playpause') {
        NativeBridge.playPause();
      } else if (event.startsWith(_incompleteMediaPrefix)) {
        _showIncompleteMediaNotice(event.substring(_incompleteMediaPrefix.length));
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await NativeBridge.setEndAction(widget.playbackEndAction);
      _brightness = await NativeBridge.getScreenBrightness();
      _volume = await NativeBridge.getMusicVolume();
      await NativeBridge.setShuffle(_shuffle);
      await NativeBridge.playAt(widget.initialIndex);
      await NativeBridge.setSpeed(_playbackSpeed);
    });
  }

  @override
  void dispose() {
    _adjustIndicatorTimer?.cancel();
    _playerUiSubscription?.cancel();
    _playerSubscription?.cancel();
    SystemChrome.setPreferredOrientations(const [DeviceOrientation.portraitUp]);
    NativeBridge.setScreenBrightness(-1);
    NativeBridge.setSpeed(1.0);
    super.dispose();
  }

  /// 文件被截断时原生会停住并上报缺失字节数：这里说明原因，不再静默跳到下一条。
  void _showIncompleteMediaNotice(String rawMissingBytes) {
    final missingBytes = int.tryParse(rawMissingBytes) ?? 0;
    if (missingBytes <= 0) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          '视频文件不完整，缺少约 ${formatByteSize(missingBytes)} 数据，无法继续播放',
        ),
        duration: const Duration(seconds: 8),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onLongPressStart: (_) => _startHoldSpeed(),
                onLongPressEnd: (_) => _endHoldSpeed(),
                onLongPressCancel: _endHoldSpeed,
                onHorizontalDragStart: _startSeekGesture,
                onHorizontalDragUpdate: _updateSeekGesture,
                onHorizontalDragEnd: (_) => _finishSeekGesture(),
                onHorizontalDragCancel: _cancelSeekGesture,
                child: AndroidView(
                  viewType: 'myplayer/player',
                  // Tap/vertical-drag recognizers belong to the platform view's
                  // gesture team so native ViewPager2 receives the complete
                  // sequence for taps and feed paging without delay.
                  gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
                    Factory<TapGestureRecognizer>(() => TapGestureRecognizer()),
                    Factory<VerticalDragGestureRecognizer>(
                      () => VerticalDragGestureRecognizer(),
                    ),
                  },
                ),
              ),
            ),
            // 左右各 20% 区域：竖滑调节亮度/音量；中间区域保持原生竖滑换视频。
            _adjustLayer(isLeft: true),
            _adjustLayer(isLeft: false),
            if (_controlsVisible)
              Positioned(
                left: 8,
                right: 8,
                top: 4,
                child: Row(
                  children: [
                    IconButton(
                      onPressed: () => Navigator.of(context).pop(),
                      color: Colors.white,
                      icon: const Icon(Icons.arrow_back_rounded),
                    ),
                    Expanded(
                      child: Text(
                        _title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: _shuffle ? '随机播放' : '顺序播放',
                      onPressed: _toggleShuffle,
                      color: _shuffle ? const Color(0xFF1FC196) : Colors.white,
                      icon: Icon(
                        _shuffle
                            ? Icons.shuffle_rounded
                            : Icons.format_list_numbered_rounded,
                      ),
                    ),
                    IconButton(
                      tooltip: '长按倍速设置',
                      onPressed: _showSpeedSheet,
                      color: Colors.white,
                      icon: const Icon(Icons.speed_rounded),
                    ),
                  ],
                ),
              ),
            if (_holdingSpeed)
              Positioned(
                left: 0,
                right: 0,
                top: 12,
                child: Align(
                  alignment: Alignment.topCenter,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.62),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 10,
                      ),
                      child: Text(
                        '${_formatSpeed(_holdSpeed)}速播放中',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            if (_adjustIndicatorValue != null)
              Positioned(
                left: 0,
                right: 0,
                top: 60,
                child: Align(
                  alignment: Alignment.topCenter,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.62),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 10,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            _adjustIsBrightness
                                ? Icons.brightness_6_rounded
                                : Icons.volume_up_rounded,
                            color: Colors.white,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '${(_adjustIndicatorValue! * 100).round()}%',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            if (_seekGesturePreviewPosition != null)
              Center(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.68),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 18,
                      vertical: 10,
                    ),
                    child: Text(
                      _formatSeekPreview(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            if (_controlsVisible)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: _BottomPlayerControls(
                  landscape: _landscape,
                  portrait: _portrait,
                  isPlaying: _isPlaying,
                  position: _position,
                  duration: _duration,
                  onSeek: (position) => NativeBridge.seekTo(position),
                  onPrevious: NativeBridge.previous,
                  onNext: NativeBridge.next,
                  onPlayPause: NativeBridge.playPause,
                  onBack:
                      () => NativeBridge.seekBy(const Duration(seconds: -5)),
                  onForward:
                      () => NativeBridge.seekBy(const Duration(seconds: 15)),
                  onHoldStart: _startHoldSpeed,
                  onHoldEnd: _endHoldSpeed,
                  onLandscape: _toggleLandscape,
                  onPortrait: _togglePortrait,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleShuffle() async {
    setState(() => _shuffle = !_shuffle);
    await NativeBridge.setShuffle(_shuffle);
    if (_shuffle) _markRandomIndex(_currentIndex);
  }

  void _markRandomIndex(int index) {
    if (!_shuffle || _lastMarkedRandomIndex == index) return;
    if (index < 0 || index >= widget.playlist.length) return;
    _lastMarkedRandomIndex = index;
    NativeBridge.markRandomPlayed(widget.playlist[index].uri);
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
  }

  void _startHoldSpeed() {
    if (_holdingSpeed) return;
    _holdingSpeed = true;
    NativeBridge.setSpeed(_holdSpeed);
  }

  void _endHoldSpeed() {
    if (!_holdingSpeed) return;
    _holdingSpeed = false;
    NativeBridge.setSpeed(_playbackSpeed);
  }

  String _formatSpeed(double speed) {
    return '${speed.toStringAsFixed(speed == speed.roundToDouble() ? 0 : 1)}x';
  }

  void _startSeekGesture(DragStartDetails details) {
    if (_duration <= Duration.zero) return;
    setState(() {
      _seekGestureStartPosition = _position;
      _seekGesturePreviewPosition = _position;
      _seekGestureDelta = 0;
      _controlsVisible = true;
    });
  }

  void _updateSeekGesture(DragUpdateDetails details) {
    final start = _seekGestureStartPosition;
    if (start == null || _duration <= Duration.zero) return;

    _seekGestureDelta += details.primaryDelta ?? 0;
    final width = MediaQuery.sizeOf(context).width.clamp(1.0, double.infinity);
    final seekWindowMs = _seekWindow.inMilliseconds;
    final offsetMs = (_seekGestureDelta / width * seekWindowMs).round();
    final targetMs = (start.inMilliseconds + offsetMs).clamp(
      0,
      _duration.inMilliseconds,
    );
    setState(() {
      _seekGesturePreviewPosition = Duration(milliseconds: targetMs);
    });
  }

  void _finishSeekGesture() {
    final preview = _seekGesturePreviewPosition;
    if (preview != null) {
      NativeBridge.seekTo(preview);
    }
    setState(() {
      _seekGestureStartPosition = null;
      _seekGesturePreviewPosition = null;
      _seekGestureDelta = 0;
    });
  }

  void _cancelSeekGesture() {
    setState(() {
      _seekGestureStartPosition = null;
      _seekGesturePreviewPosition = null;
      _seekGestureDelta = 0;
    });
  }

  /// 左右两侧 20% 宽的透明手势层：左侧竖滑调亮度、右侧竖滑调音量。
  /// 单击/双击在本层直接处理（不经原生转发，响应更快）。
  Widget _adjustLayer({required bool isLeft}) {
    final width = MediaQuery.sizeOf(context).width * 0.2;
    return Positioned(
      left: isLeft ? 0 : null,
      right: isLeft ? null : 0,
      top: 0,
      bottom: 0,
      width: width,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: _toggleControls,
        onDoubleTap: NativeBridge.playPause,
        onVerticalDragStart: (details) => _startAdjustGesture(isLeft, details),
        onVerticalDragUpdate: _updateAdjustGesture,
        onVerticalDragEnd: (_) => _finishAdjustGesture(),
        onVerticalDragCancel: _finishAdjustGesture,
        child: const SizedBox.expand(),
      ),
    );
  }

  void _startAdjustGesture(bool isBrightness, DragStartDetails details) {
    _adjustIsBrightness = isBrightness;
    _adjustStartDy = details.globalPosition.dy;
    _adjustBaseValue = isBrightness ? _brightness : _volume;
  }

  void _updateAdjustGesture(DragUpdateDetails details) {
    final startDy = _adjustStartDy;
    final base = _adjustBaseValue;
    if (startDy == null || base == null) return;
    // 全屏滑动 ≈ 完整调整范围；上滑增大、下滑减小。
    final span = MediaQuery.sizeOf(context).height.clamp(1.0, double.infinity);
    final moved = (startDy - details.globalPosition.dy) / span;
    var next = (base + moved).clamp(0.0, 1.0);
    // 亮度 0 会直接黑屏看不到指示器，保底 2%。
    if (_adjustIsBrightness) next = next.clamp(0.02, 1.0);
    setState(() => _adjustIndicatorValue = next);
    if (_adjustIsBrightness) {
      _brightness = next;
      NativeBridge.setScreenBrightness(next);
    } else {
      _volume = next;
      NativeBridge.setMusicVolume(next);
    }
  }

  void _finishAdjustGesture() {
    if (_adjustIndicatorValue == null) return;
    _adjustIndicatorTimer?.cancel();
    _adjustIndicatorTimer = Timer(const Duration(milliseconds: 800), () {
      if (!mounted) return;
      setState(() => _adjustIndicatorValue = null);
    });
  }

  Duration get _seekWindow {
    final tenPercent = (_duration.inMilliseconds * 0.1).round();
    final clamped = tenPercent.clamp(30000, 120000);
    return Duration(milliseconds: clamped);
  }

  String _formatSeekPreview() {
    final preview = _seekGesturePreviewPosition ?? _position;
    final delta = preview - (_seekGestureStartPosition ?? _position);
    final sign = delta.isNegative ? '-' : '+';
    return '${_formatDuration(preview)}  $sign${_formatDuration(delta.abs())}';
  }

  String _formatDuration(Duration duration) {
    final totalSeconds = duration.inSeconds;
    final hours = totalSeconds ~/ 3600;
    final minutes = (totalSeconds % 3600) ~/ 60;
    final seconds = totalSeconds % 60;
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  Future<void> _toggleLandscape() async {
    final next = !_landscape;
    setState(() {
      _landscape = next;
      _portrait = false;
    });
    await SystemChrome.setPreferredOrientations(
      next
          ? [DeviceOrientation.landscapeLeft]
          : [DeviceOrientation.portraitUp],
    );
  }

  Future<void> _togglePortrait() async {
    final next = !_portrait;
    setState(() {
      _portrait = next;
      _landscape = false;
    });
    await SystemChrome.setPreferredOrientations(const [DeviceOrientation.portraitUp]);
  }

  Future<void> _showSpeedSheet() async {
    final selected = await showModalBottomSheet<_SpeedChoice>(
      context: context,
      backgroundColor: const Color(0xFF161616),
      builder: (context) {
        final speeds = [0.75, 1.0, 1.25, 1.5, 2.0, 3.0];
        final holdSpeeds = [1.5, 2.0, 2.5, 3.0, 4.0];
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '播放速度',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children:
                      speeds.map((speed) {
                        return ChoiceChip(
                          label: Text(_formatSpeed(speed)),
                          selected: _playbackSpeed == speed,
                          onSelected:
                              (_) => Navigator.of(
                                context,
                              ).pop(_SpeedChoice.playback(speed)),
                        );
                      }).toList(),
                ),
                const SizedBox(height: 18),
                const Text(
                  '长按倍速',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children:
                      holdSpeeds.map((speed) {
                        return ChoiceChip(
                          label: Text(_formatSpeed(speed)),
                          selected: _holdSpeed == speed,
                          onSelected:
                              (_) => Navigator.of(
                                context,
                              ).pop(_SpeedChoice.hold(speed)),
                        );
                      }).toList(),
                ),
              ],
            ),
          ),
        );
      },
    );
    if (selected != null && mounted) {
      if (selected.kind == _SpeedChoiceKind.playback) {
        setState(() => _playbackSpeed = selected.speed);
        NativeBridge.setSpeed(selected.speed);
      } else {
        setState(() => _holdSpeed = selected.speed);
      }
    }
  }
}

enum _SpeedChoiceKind { playback, hold }

class _SpeedChoice {
  const _SpeedChoice(this.kind, this.speed);

  factory _SpeedChoice.playback(double speed) {
    return _SpeedChoice(_SpeedChoiceKind.playback, speed);
  }

  factory _SpeedChoice.hold(double speed) {
    return _SpeedChoice(_SpeedChoiceKind.hold, speed);
  }

  final _SpeedChoiceKind kind;
  final double speed;
}

class _BottomPlayerControls extends StatelessWidget {
  const _BottomPlayerControls({
    required this.landscape,
    required this.portrait,
    required this.isPlaying,
    required this.position,
    required this.duration,
    required this.onSeek,
    required this.onPrevious,
    required this.onNext,
    required this.onPlayPause,
    required this.onBack,
    required this.onForward,
    required this.onHoldStart,
    required this.onHoldEnd,
    required this.onLandscape,
    required this.onPortrait,
  });

  final bool landscape;
  final bool portrait;
  final bool isPlaying;
  final Duration position;
  final Duration duration;
  final ValueChanged<Duration> onSeek;
  final VoidCallback onPrevious;
  final VoidCallback onNext;
  final VoidCallback onPlayPause;
  final VoidCallback onBack;
  final VoidCallback onForward;
  final VoidCallback onHoldStart;
  final VoidCallback onHoldEnd;
  final VoidCallback onLandscape;
  final VoidCallback onPortrait;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 16),
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Color(0xCC000000), Colors.black],
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _PlayerProgressBar(
            position: position,
            duration: duration,
            onSeek: onSeek,
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _ControlButton(
                icon: Icons.skip_previous_rounded,
                onPressed: onPrevious,
              ),
              _ControlButton(icon: Icons.replay_5_rounded, onPressed: onBack),
              GestureDetector(
                onLongPressStart: (_) => onHoldStart(),
                onLongPressEnd: (_) => onHoldEnd(),
                child: _ControlButton(
                  icon:
                      isPlaying
                          ? Icons.pause_rounded
                          : Icons.play_arrow_rounded,
                  prominent: true,
                  onPressed: onPlayPause,
                ),
              ),
              _ControlButton(
                icon: Icons.forward_10_rounded,
                onPressed: onForward,
              ),
              _ControlButton(icon: Icons.skip_next_rounded, onPressed: onNext),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              const Spacer(),
              _ModeButton(
                selected: portrait,
                icon: Icons.stay_current_portrait_rounded,
                label: '竖屏',
                onPressed: onPortrait,
              ),
              const SizedBox(width: 8),
              _ModeButton(
                selected: landscape,
                icon: Icons.stay_current_landscape_rounded,
                label: '横屏',
                onPressed: onLandscape,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PlayerProgressBar extends StatefulWidget {
  const _PlayerProgressBar({
    required this.position,
    required this.duration,
    required this.onSeek,
  });

  final Duration position;
  final Duration duration;
  final ValueChanged<Duration> onSeek;

  @override
  State<_PlayerProgressBar> createState() => _PlayerProgressBarState();
}

class _PlayerProgressBarState extends State<_PlayerProgressBar> {
  double? _dragValue;

  @override
  Widget build(BuildContext context) {
    final durationMs = widget.duration.inMilliseconds;
    final positionMs = widget.position.inMilliseconds.clamp(0, durationMs);
    final value = _dragValue ?? (durationMs <= 0 ? 0.0 : positionMs.toDouble());

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            trackHeight: 3,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 12),
            activeTrackColor: Colors.white,
            inactiveTrackColor: Colors.white24,
            thumbColor: Colors.white,
          ),
          child: Slider(
            min: 0,
            max: durationMs <= 0 ? 1 : durationMs.toDouble(),
            value: value.clamp(0, durationMs <= 0 ? 1 : durationMs.toDouble()),
            onChanged:
                durationMs <= 0
                    ? null
                    : (next) => setState(() => _dragValue = next),
            onChangeEnd:
                durationMs <= 0
                    ? null
                    : (next) {
                      setState(() => _dragValue = null);
                      widget.onSeek(Duration(milliseconds: next.round()));
                    },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            children: [
              Text(
                _formatDuration(Duration(milliseconds: positionMs)),
                style: const TextStyle(color: Colors.white, fontSize: 12),
              ),
              const Spacer(),
              Text(
                _formatDuration(widget.duration),
                style: const TextStyle(color: Colors.white70, fontSize: 12),
              ),
            ],
          ),
        ),
      ],
    );
  }

  String _formatDuration(Duration duration) {
    final totalSeconds = duration.inSeconds;
    final hours = totalSeconds ~/ 3600;
    final minutes = (totalSeconds % 3600) ~/ 60;
    final seconds = totalSeconds % 60;
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }
}

class _ControlButton extends StatelessWidget {
  const _ControlButton({
    required this.icon,
    required this.onPressed,
    this.prominent = false,
  });

  final IconData icon;
  final VoidCallback onPressed;
  final bool prominent;

  @override
  Widget build(BuildContext context) {
    return IconButton.filled(
      style: IconButton.styleFrom(
        backgroundColor:
            prominent ? Colors.white : Colors.white.withValues(alpha: 0.14),
        foregroundColor: prominent ? Colors.black : Colors.white,
        minimumSize: Size.square(prominent ? 54 : 44),
      ),
      onPressed: onPressed,
      icon: Icon(icon, size: prominent ? 32 : 28),
    );
  }
}

class _ModeButton extends StatelessWidget {
  const _ModeButton({
    required this.selected,
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final bool selected;
  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      style: TextButton.styleFrom(
        foregroundColor: selected ? const Color(0xFF1FC196) : Colors.white70,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      ),
      onPressed: onPressed,
      icon: Icon(icon, size: 18),
      label: Text(label, style: const TextStyle(fontSize: 12)),
    );
  }
}
