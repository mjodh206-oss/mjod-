package com.amjadhabib.rattib

import android.content.Context
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {

    private val channelName = "com.amjadhabib.rattib/widget"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                if (call.method == "updateWidget") {
                    val remaining = call.argument<String>("remainingText") ?: "0"
                    val daysLeft = call.argument<String>("daysLeftText") ?: ""
                    val progress = call.argument<Int>("progressPercent") ?: 100
                    val subtitle = call.argument<String>("subtitleText") ?: "فلوسك بتستاهل خطة"

                    val prefs = applicationContext.getSharedPreferences(
                        RattibWidgetProvider.PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    prefs.edit()
                        .putString(RattibWidgetProvider.KEY_REMAINING, remaining)
                        .putString(RattibWidgetProvider.KEY_DAYS_LEFT, daysLeft)
                        .putInt(RattibWidgetProvider.KEY_PROGRESS, progress)
                        .putString(RattibWidgetProvider.KEY_SUBTITLE, subtitle)
                        .apply()

                    RattibWidgetProvider.updateAllWidgets(applicationContext)
                    result.success(true)
                } else {
                    result.notImplemented()
                }
            }
    }
}
