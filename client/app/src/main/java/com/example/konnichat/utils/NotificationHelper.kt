package com.example.konnichat.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.konnichat.R
import com.example.konnichat.ui.home.HomeActivity

object NotificationHelper {

    private const val CHANNEL_ID = "konnichat_channel_v1"
    private const val CHANNEL_NAME = "KonniChat Notifications"
    private const val NOTIFICATION_ID_FRIEND_REQ = 1001

    // Khởi tạo kênh thông báo (Bắt buộc cho Android 8.0+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Thông báo tin nhắn và lời mời kết bạn"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Hiển thị thông báo.
     * @param type: Loại thông báo (để xác định hành động click). VD: "FRIEND_REQ", "MESSAGE"
     */
    fun showNotification(context: Context, title: String, content: String, type: String) {
        // Intent mở App khi click vào thông báo
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Truyền type để HomeActivity biết nên mở Tab nào
            putExtra("NAVIGATE_TO", type)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Icon nhỏ trên thanh status
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Tự đóng khi click

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Nếu chưa có quyền thì không hiện thông báo để tránh crash
                return
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FRIEND_REQ, builder.build())
    }
}