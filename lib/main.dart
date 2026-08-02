import 'dart:async';
import 'dart:math';

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
}

class AppSettings {
  const AppSettings({
    this.defaultPlaybackSpeed = 1.0,
    this.randomIncludeSubfolders = false,
  });

  final double defaultPlaybackSpeed;
  final bool randomIncludeSubfolders;

  factory AppSettings.fromMap(Map<String, dynamic> map) {
    return AppSettings(
      defaultPlaybackSpeed:
          (map['defaultPlaybackSpeed'] as num? ?? 1.0).toDouble(),
      randomIncludeSubfolders:
          map['randomIncludeSubfolders'] as bool? ?? false,
    );
  }

  AppSettings copyWith({
    double? defaultPlaybackSpeed,
    bool? randomIncludeSubfolders,
  }) {
    return AppSettings(
      defaultPlaybackSpeed: defaultPlaybackSpeed ?? this.defaultPlaybackSpeed,
      randomIncludeSubfolders:
          randomIncludeSubfolders ?? this.randomIncludeSubfolders,
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
  });

  final String uri;
  final String displayName;
  final String fileName;
  final int size;
  final int lastModified;
  final int xorUntilOffset;

  factory EncryptedVideo.fromMap(Map<String, dynamic> map) {
    return EncryptedVideo(
      uri: map['uri'] as String,
      displayName: map['displayName'] as String,
      fileName: map['fileName'] as String? ?? map['displayName'] as String,
      size: (map['size'] as num).toInt(),
      lastModified: (map['lastModified'] as num).toInt(),
      xorUntilOffset: (map['xorUntilOffset'] as num).toInt(),
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
    };
  }
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
  StreamSubscription<Map<String, dynamic>>? _progressSubscription;

  List<BrowserEntry> _entries = const [];
  List<EncryptedVideo> _videos = const [];
  List<BrowserEntry> _folderStack = const [];
  AppSettings _settings = const AppSettings();
  ScanProgress? _progress;
  String _message = '请选择根目录';
  bool _scanning = false;

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
        _scanning = progress.phase != 'done';
        _message = _scanMessage(progress);
        if (phase == 'reset') {
          _entries = const [];
          _videos = const [];
          _folderStack = const [];
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
        }
      });
    });
    _restore();
  }

  @override
  void dispose() {
    _progressSubscription?.cancel();
    super.dispose();
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
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
  }

  Future<void> _playVideo(
    EncryptedVideo video, {
    bool shuffle = false,
    List<EncryptedVideo>? playlistOverride,
  }) async {
    final playlist = playlistOverride ?? _currentVideos;
    final index = playlist.indexWhere((item) => item.uri == video.uri);
    if (index < 0) return;
    await NativeBridge.setPlaylist(playlist);
    await NativeBridge.setShuffle(shuffle);
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder:
            (_) => PlayerPage(
              title: video.displayName,
              initialIndex: index,
              shuffle: shuffle,
              playlist: playlist,
              defaultPlaybackSpeed: _settings.defaultPlaybackSpeed,
            ),
      ),
    );
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
            title: _currentTitle,
            canGoBack: _folderStack.isNotEmpty,
            scanning: _scanning,
            progress: _progress,
            message: _message,
            onBack: _goUpFolder,
            onChooseFolder: _chooseFolder,
            onRefresh: _refresh,
            onOpenSettings: _openSettings,
          ),
          Expanded(
            child:
                currentEntries.isEmpty
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
                        );
                      },
                    ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.small(
        heroTag: 'randomPlay',
        backgroundColor: const Color(0xFF1BB98B),
        foregroundColor: Colors.white,
        onPressed: _randomPlay,
        tooltip: '随机播放',
        child: const Icon(Icons.shuffle_rounded, size: 22),
      ),
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
          SwitchListTile(
            secondary: const Icon(Icons.account_tree_rounded),
            title: const Text('随机子文件夹视频'),
            subtitle: const Text('随机播放时包含当前目录下的子文件夹视频'),
            value: _settings.randomIncludeSubfolders,
            onChanged: _setRandomIncludeSubfolders,
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
                    : const Icon(Icons.chevron_right_rounded),
            onTap: _clearingRandomHistory ? null : _clearRandomHistory,
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

  Future<void> _setRandomIncludeSubfolders(bool enabled) async {
    await NativeBridge.setRandomIncludeSubfolders(enabled);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(randomIncludeSubfolders: enabled);
    });
  }

  Future<void> _clearRandomHistory() async {
    setState(() => _clearingRandomHistory = true);
    await NativeBridge.clearRandomPlayed();
    if (!mounted) return;
    setState(() => _clearingRandomHistory = false);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('已清除随机播放记录')));
  }

  String _formatSpeed(double speed) {
    return '${speed.toStringAsFixed(speed == speed.roundToDouble() ? 0 : 1)}x';
  }
}

class BrowserEntryTile extends StatelessWidget {
  const BrowserEntryTile({super.key, required this.entry, required this.onTap});

  final BrowserEntry entry;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final isFolder = entry.isFolder;
    return InkWell(
      onTap: onTap,
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
                    isFolder
                        ? '${entry.videoCount} 个视频'
                        : _formatSize(entry.size),
                    style: const TextStyle(
                      color: Color(0xFF92A0B8),
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

  String _formatSize(int size) {
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
  });

  final String title;
  final int initialIndex;
  final bool shuffle;
  final List<EncryptedVideo> playlist;
  final double defaultPlaybackSpeed;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  StreamSubscription<PlayerPlaybackState>? _playerSubscription;
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
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await NativeBridge.setShuffle(_shuffle);
      await NativeBridge.playAt(widget.initialIndex);
      await NativeBridge.setSpeed(_playbackSpeed);
    });
  }

  @override
  void dispose() {
    _playerSubscription?.cancel();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    NativeBridge.setSpeed(1.0);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            const Positioned.fill(
              child: AndroidView(viewType: 'myplayer/player'),
            ),
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onTap: _toggleControls,
                onDoubleTap: NativeBridge.playPause,
                onLongPressStart: (_) => _startHoldSpeed(),
                onLongPressEnd: (_) => _endHoldSpeed(),
                onLongPressCancel: _endHoldSpeed,
                onHorizontalDragStart: _startSeekGesture,
                onHorizontalDragUpdate: _updateSeekGesture,
                onHorizontalDragEnd: (_) => _finishSeekGesture(),
                onHorizontalDragCancel: _cancelSeekGesture,
              ),
            ),
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
              Center(
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
          ? [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight]
          : DeviceOrientation.values,
    );
  }

  Future<void> _togglePortrait() async {
    final next = !_portrait;
    setState(() {
      _portrait = next;
      _landscape = false;
    });
    await SystemChrome.setPreferredOrientations(
      next
          ? [DeviceOrientation.portraitUp, DeviceOrientation.portraitDown]
          : DeviceOrientation.values,
    );
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
