import 'package:flutter/material.dart';

import 'app.dart';
import 'services/notification_service.dart';
import 'services/pdf_export_service.dart';
import 'services/security_service.dart';
import 'services/sound_service.dart';
import 'services/storage_service.dart';
import 'services/widget_sync_service.dart';
import 'state/app_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final storage = StorageService();
  await storage.init();

  final notifications = NotificationService();
  await notifications.init();

  final controller = AppController(
    storage: storage,
    sound: SoundService(),
    notifications: notifications,
    security: SecurityService(),
    widgetSync: WidgetSyncService(),
    pdfExport: PdfExportService(),
  );
  await controller.initialize();

  runApp(RattibApp(controller: controller));
}
