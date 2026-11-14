import 'package:flutter/services.dart';

class WidgetSyncService {
  static const MethodChannel _channel =
      MethodChannel('com.amjadhabib.rattib/widget');

  Future<void> updateWidget({
    required String remainingText,
    required String daysLeftText,
    required int progressPercent,
    required String subtitleText,
  }) async {
    try {
      await _channel.invokeMethod<bool>('updateWidget', {
        'remainingText': remainingText,
        'daysLeftText': daysLeftText,
        'progressPercent': progressPercent.clamp(0, 100),
        'subtitleText': subtitleText,
      });
    } catch (_) {
      // Safe no-op in widget tests / non-Android hosts
    }
  }
}
