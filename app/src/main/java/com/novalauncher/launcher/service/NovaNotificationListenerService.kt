package com.novalauncher.launcher.service

import android.app.Notification
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Structured model representing an active system notification.
 */
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val title: String,
    val body: String,
    val icon: Drawable?,
    val timestamp: Long,
    val color: Int,
    val isGroupSummary: Boolean = false,
    val groupKey: String? = null
)

/**
 * Callback listener interface for observing posted and removed notifications.
 */
interface NotificationListener {
    fun onNotificationPosted(info: NotificationInfo)
    fun onNotificationRemoved(key: String)
}

/**
 * Production-grade NotificationListenerService for NovaLauncher.
 *
 * Responsibilities:
 * - Maintains an active in-memory cache of posted notifications.
 * - Filters and resolves grouped notifications and summary cards.
 * - Dispatches updates to registered [NotificationListener] observers via WeakReferences.
 * - Exposes singleton access via [instance] and [getInstance].
 */
class NovaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NovaNotifService"

        @Volatile
        @JvmField
        var instance: NovaNotificationListenerService? = null

        @JvmStatic
        fun getInstance(): NovaNotificationListenerService? = instance
    }

    // Weakly-held listener references to prevent memory leaks with activities and overlay views
    private val listeners = CopyOnWriteArrayList<WeakReference<NotificationListener>>()

    // In-memory cache of current notifications keyed by StatusBarNotification.key
    private val notificationMap = LinkedHashMap<String, NotificationInfo>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NovaNotificationListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        synchronized(notificationMap) {
            notificationMap.clear()
        }
        listeners.clear()
        Log.i(TAG, "NovaNotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected, synchronizing active notifications")
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    // ── Listener Registration (Memory-Safe via WeakReference) ───────────────

    fun addListener(listener: NotificationListener) {
        // Clean up dead references first
        cleanDeadReferences()
        // Prevent duplicate registrations
        val alreadyExists = listeners.any { it.get() === listener }
        if (!alreadyExists) {
            listeners.add(WeakReference(listener))
            // Immediately replay active notifications to new listener
            synchronized(notificationMap) {
                notificationMap.values.forEach { info ->
                    listener.onNotificationPosted(info)
                }
            }
        }
    }

    fun removeListener(listener: NotificationListener) {
        listeners.removeAll { it.get() === null || it.get() === listener }
    }

    private fun cleanDeadReferences() {
        listeners.removeAll { it.get() == null }
    }

    // ── Active Notifications Query ───────────────────────────────────────────

    fun getActiveNotificationsList(): List<NotificationInfo> {
        synchronized(notificationMap) {
            return notificationMap.values.toList()
        }
    }

    fun getNotification(key: String): NotificationInfo? {
        synchronized(notificationMap) {
            return notificationMap[key]
        }
    }

    /**
     * Dismiss a notification by its key.
     */
    fun dismissNotification(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification: $key", e)
        }
    }

    // ── Lifecycle Callbacks from Android System ──────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val info = extractNotificationInfo(sbn) ?: return

        synchronized(notificationMap) {
            notificationMap[info.key] = info
        }

        dispatchNotificationPosted(info)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key

        synchronized(notificationMap) {
            notificationMap.remove(key)
        }

        dispatchNotificationRemoved(key)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────

    private fun syncActiveNotifications() {
        try {
            val sbns = activeNotifications ?: return
            synchronized(notificationMap) {
                notificationMap.clear()
                for (sbn in sbns) {
                    val info = extractNotificationInfo(sbn)
                    if (info != null) {
                        notificationMap[info.key] = info
                    }
                }
            }

            // Notify all current listeners
            val currentList = getActiveNotificationsList()
            for (info in currentList) {
                dispatchNotificationPosted(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing active notifications: ${e.message}", e)
        }
    }

    private fun extractNotificationInfo(sbn: StatusBarNotification): NotificationInfo? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        // Skip blank notifications (e.g. internal system alerts without content)
        if (title.isBlank() && text.isBlank()) {
            return null
        }

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val groupKey = sbn.groupKey

        // Extract app icon safely
        val icon: Drawable? = try {
            packageManager.getApplicationIcon(sbn.packageName)
        } catch (_: Exception) {
            null
        }

        val color = notification.color
        val timestamp = sbn.postTime

        return NotificationInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            body = text,
            icon = icon,
            timestamp = timestamp,
            color = color,
            isGroupSummary = isGroupSummary,
            groupKey = groupKey
        )
    }

    private fun dispatchNotificationPosted(info: NotificationInfo) {
        cleanDeadReferences()
        for (ref in listeners) {
            ref.get()?.onNotificationPosted(info)
        }
    }

    private fun dispatchNotificationRemoved(key: String) {
        cleanDeadReferences()
        for (ref in listeners) {
            ref.get()?.onNotificationRemoved(key)
        }
    }
}

