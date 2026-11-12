package com.novalauncher.launcher.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import org.xmlpull.v1.XmlPullParser

/**
 * Data model representing an installed third-party icon pack.
 */
data class IconPackInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable
)

/**
 * Manager for discovering, caching, and loading application icons from third-party icon packs.
 *
 * Supported icon pack theme standards:
 * - ADW Launcher Theme (`org.adw.launcher.THEMES`)
 * - Nova / Apex Launcher Theme (`com.teslacoilsw.launcher.THEME`, `com.anddoes.launcher.THEME`)
 * - Go Launcher Theme (`com.gau.go.launcherex.theme`)
 *
 * Features:
 * - Automatic appfilter.xml parsing and caching for fast drawable resolution
 * - LruCache bounded at 128 icons to conserve memory on low-RAM devices
 * - Graceful fallback to default app icon
 */
object IconPackManager {

    private const val TAG = "IconPackManager"
    private const val CACHE_SIZE = 128

    // Memory-bounded LRU cache for resolved icon drawables
    private val iconCache = LruCache<String, Drawable>(CACHE_SIZE)

    // Cached appfilter mappings per icon pack: Map<IconPackPackage, Map<AppPackage, DrawableResourceName>>
    private val packFilterCache = HashMap<String, Map<String, String>>()

    private val ICON_PACK_ACTIONS = arrayOf(
        "org.adw.launcher.THEMES",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME"
    )

    /**
     * Clears cached icons and parsed theme mappings.
     */
    fun clearCache() {
        iconCache.evictAll()
        synchronized(packFilterCache) {
            packFilterCache.clear()
        }
    }

    /**
     * Discovers all installed icon packs on the device.
     *
     * @param context Application context
     * @return List of [IconPackInfo] for all compatible installed icon packs
     */
    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = LinkedHashMap<String, IconPackInfo>()

        for (action in ICON_PACK_ACTIONS) {
            val intent = Intent(action)
            val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in activities) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (!packs.containsKey(pkgName)) {
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    packs[pkgName] = IconPackInfo(
                        packageName = pkgName,
                        name = label,
                        icon = icon
                    )
                }
            }
        }

        return packs.values.toList()
    }

    /**
     * Loads the icon for a specific app package. If an icon pack is specified,
     * it attempts to retrieve the themed icon from that pack. If not found or if
     * [iconPackPackage] is null, falls back to the app's default system icon.
     *
     * @param context Application context
     * @param packageName Target app's package name
     * @param iconPackPackage Optional package name of selected icon pack
     * @return Drawable of the icon, or null if app cannot be resolved
     */
    fun loadIconForPackage(
        context: Context,
        packageName: String,
        iconPackPackage: String? = null
    ): Drawable? {
        val pm = context.packageManager

        // If no icon pack is selected, return the default app icon
        if (iconPackPackage.isNullOrEmpty()) {
            return try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }

        val cacheKey = "$iconPackPackage:$packageName"
        iconCache.get(cacheKey)?.let { return it }

        var themedDrawable: Drawable? = null
        try {
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
            val drawableName = getThemedDrawableName(context, iconPackPackage, iconPackRes, packageName)

            if (!drawableName.isNullOrEmpty()) {
                val resId = iconPackRes.getIdentifier(drawableName, "drawable", iconPackPackage)
                if (resId != 0) {
                    themedDrawable = iconPackRes.getDrawable(resId, null)
                }
            }

            // Fallback try: sanitized package name as drawable resource (e.g., com_android_chrome)
            if (themedDrawable == null) {
                val sanitizedName = packageName.replace('.', '_').lowercase()
                val fallbackResId = iconPackRes.getIdentifier(sanitizedName, "drawable", iconPackPackage)
                if (fallbackResId != 0) {
                    themedDrawable = iconPackRes.getDrawable(fallbackResId, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading icon for $packageName from pack $iconPackPackage: ${e.message}")
        }

        // If not found in the icon pack, fall back to default app icon
        val resultDrawable = themedDrawable ?: try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        if (resultDrawable != null) {
            iconCache.put(cacheKey, resultDrawable)
        }

        return resultDrawable
    }

    /**
     * Resolves the mapped drawable name from the icon pack's appfilter.xml.
     */
    private fun getThemedDrawableName(
        context: Context,
        iconPackPackage: String,
        iconPackRes: Resources,
        targetPackage: String
    ): String? {
        val filterMap = synchronized(packFilterCache) {
            packFilterCache[iconPackPackage] ?: loadAppFilter(iconPackRes, iconPackPackage).also {
                packFilterCache[iconPackPackage] = it
            }
        }
        return filterMap[targetPackage]
    }

    /**
     * Parses the appfilter.xml resource inside the icon pack APK.
     */
    private fun loadAppFilter(res: Resources, packPackage: String): Map<String, String> {
        val mapping = HashMap<String, String>()
        val appFilterId = res.getIdentifier("appfilter", "xml", packPackage)
        if (appFilterId == 0) return mapping

        try {
            val parser = res.getXml(appFilterId)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                        // Component format is ComponentInfo{packageName/activityName}
                        val pkgName = extractPackageFromComponent(component)
                        if (pkgName.isNotEmpty()) {
                            mapping[pkgName] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse appfilter.xml for $packPackage: ${e.message}")
        }

        return mapping
    }

    private fun extractPackageFromComponent(component: String): String {
        val startIndex = component.indexOf('{')
        val slashIndex = component.indexOf('/')
        return if (startIndex != -1 && slashIndex != -1 && slashIndex > startIndex) {
            component.substring(startIndex + 1, slashIndex)
        } else {
            ""
        }
    }
}

