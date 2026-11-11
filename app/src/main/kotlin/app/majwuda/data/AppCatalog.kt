package app.majwuda.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.majwuda.core.ArabicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val packageName: String, val score: Double = 1.0)

/** Launchable apps visible to the user, Arabic labels included automatically. */
class AppCatalog(context: Context) {
    private val appContext = context.applicationContext

    suspend fun launchableApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val infos: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        infos.asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                var label = try {
                    info.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Exception) {
                    pkg
                }
                if (label.isEmpty()) label = pkg
                AppEntry(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { ArabicText.normalize(it.label) }
            .toList()
    }

    suspend fun find(query: String, limit: Int = 5, minScore: Double = 0.45): List<AppEntry> =
        withContext(Dispatchers.IO) {
            launchableApps()
                .map { it to ArabicText.matchScore(query, it.label) }
                .filter { it.second >= minScore }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first.copy(score = it.second) }
        }
}

/** Intent helpers shared by the tools. */
object AppIntents {
    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
