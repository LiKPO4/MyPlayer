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
}
