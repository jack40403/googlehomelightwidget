package com.example.googlehomeapisampleapp.widget

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.util.Log
import android.view.WindowManager

internal fun Activity.configureWidgetOverlayWindow(tag: String) {
  window.setBackgroundDrawableResource(android.R.color.transparent)
  window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
  window.attributes = window.attributes.apply { dimAmount = 0.55f }
  window.statusBarColor = AndroidColor.TRANSPARENT
  window.navigationBarColor = AndroidColor.TRANSPARENT
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    window.isNavigationBarContrastEnforced = false
  }

  val taskInfo = getSystemService(ActivityManager::class.java)
    .appTasks
    .firstOrNull { it.taskInfo.taskId == taskId }
    ?.taskInfo
  val activityInfo = packageManager.getActivityInfo(componentName, 0)
  Log.i(
    tag,
    "Overlay task: taskId=$taskId, isTaskRoot=$isTaskRoot, " +
      "base=${taskInfo?.baseActivity}, top=${taskInfo?.topActivity}, " +
      "affinity=${activityInfo.taskAffinity}, flags=0x${intent.flags.toString(16)}",
  )
}
