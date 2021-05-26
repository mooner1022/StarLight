package com.mooner.starlight.ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.logger.LogType
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.ui.logs.LogsRecyclerViewAdapter
import jp.wasabeef.recyclerview.animators.FadeInLeftAnimator


class OverlayService: Service() {

    private var windowManager: WindowManager? = null
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                300,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                300,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        }
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        view = inflater.inflate(R.layout.layout_overlay_dialog, null)
        val logsRecycler: RecyclerView = view!!.findViewById(R.id.recyclerViewLogs)
        val logs = Logger.filterNot(LogType.DEBUG)
        val logsAdapter = LogsRecyclerViewAdapter(applicationContext)
        if (logs.isNotEmpty()) {
            val recyclerLayoutManager = LinearLayoutManager(applicationContext).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            logsAdapter.data = logs.toMutableList()
            with(logsRecycler) {
                itemAnimator = FadeInLeftAnimator()
                layoutManager = recyclerLayoutManager
                adapter = logsAdapter
            }
            logsAdapter.notifyItemRangeInserted(0, logs.size)
        }

        windowManager!!.addView(view, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (windowManager != null) {
            if (view != null) {
                windowManager!!.removeView(view)
                view = null
            }
            windowManager = null
        }
    }
}