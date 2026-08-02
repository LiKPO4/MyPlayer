import 'package:flutter_test/flutter_test.dart';
import 'package:myplayer/main.dart';

void main() {
  test('scan progress parses native event payload', () {
    final progress = ScanProgress.fromMap({
      'phase': 'scanning',
      'processed': 5,
      'total': 10,
      'found': 2,
      'cached': 3,
      'skipped': 1,
      'failed': 0,
    });

    expect(progress.phase, 'scanning');
    expect(progress.fraction, 0.5);
    expect(progress.found, 2);
    expect(progress.cached, 3);
  });

  group('update version comparison', () {
    const installed = AppVersion(name: '1.0.6', code: 7);

    test('accepts a newer semantic version', () {
      expect(UpdateService.isNewer('v1.0.7+8', installed), isTrue);
    });

    test('accepts a higher build number for the same version', () {
      expect(UpdateService.isNewer('v1.0.6+8', installed), isTrue);
    });

    test('rejects the installed or an older version', () {
      expect(UpdateService.isNewer('v1.0.6+7', installed), isFalse);
      expect(UpdateService.isNewer('v1.0.5+99', installed), isFalse);
    });

    test('rejects malformed release tags', () {
      expect(UpdateService.isNewer('latest', installed), isFalse);
    });
  });
}
