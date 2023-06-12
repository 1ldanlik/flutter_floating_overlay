package com.example.flutter_floating_overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.example.flutter_overlay_example.OverlayConstants
import com.example.flutter_overlay_example.WindowSetup
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.Timer
import java.util.TimerTask

class OverlayService: Service(), OnTouchListener {
    private val DEFAULT_NAV_BAR_HEIGHT_DP = 48
    private val DEFAULT_STATUS_BAR_HEIGHT_DP = 25
    private var mStatusBarHeight = -1
    private var mNavigationBarHeight = -1
    private var mResources: Resources? = null
    private var windowManager: WindowManager? = null
    private var flutterView: FlutterView? = null
    private val flutterChannel = MethodChannel(
        FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]!!.dartExecutor,
        OverlayConstants.OVERLAY_TAG
    )
    private val overlayMessageChannel = BasicMessageChannel<Any?>(
        FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]!!.dartExecutor,
        OverlayConstants.MESSENGER_TAG,
        JSONMessageCodec.INSTANCE
    )
    private val clickableFlag =
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    private val mAnimationHandler = Handler()
    private var lastX = 0f
    private var lastY = 0f
    private var lastYPosition = 0
    private var dragging = false
    private val szWindow = Point()
    private var mTrayAnimationTimer: Timer? = null
    private var mTrayTimerTask: TrayAnimationTimerTask? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onDestroy() {
        Log.d("OverLay", "Destroying the overlay window service")
        isRunning = false
        val notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(OverlayConstants.NOTIFICATION_ID)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mResources = applicationContext.resources
        val isCloseWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, false)
        if (isCloseWindow) {
            if (windowManager != null) {
                windowManager!!.removeView(flutterView)
                windowManager = null
                flutterView!!.detachFromFlutterEngine()
                stopSelf()
            }
            isRunning = false
            return START_STICKY
        }
        if (windowManager != null) {
            windowManager!!.removeView(flutterView)
            windowManager = null
            flutterView!!.detachFromFlutterEngine()
            stopSelf()
        }
        isRunning = true
        Log.d("onStartCommand", "Service started")
        val engine = FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]
        engine!!.lifecycleChannel.appIsResumed()
        flutterView = FlutterView(
            applicationContext, FlutterTextureView(
                applicationContext
            )
        )
        flutterView!!.attachToFlutterEngine(FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]!!)
        flutterView!!.fitsSystemWindows = true
        flutterView!!.isFocusable = true
        flutterView!!.isFocusableInTouchMode = true
        flutterView!!.setBackgroundColor(Color.TRANSPARENT)
        flutterChannel.setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
            if ((call.method == "updateFlag")) {
                val flag: String = call.argument<Any>("flag").toString()
                updateOverlayFlag(result, flag)
            } else if ((call.method == "resizeOverlay")) {
                val width: Int = (call.argument<Int>("width")!!)
                val height: Int = (call.argument<Int>("height")!!)
                resizeOverlay(width, height, result)
            }
        }
//        overlayMessageChannel.setMessageHandler({ message: Any?, reply: BasicMessageChannel.Reply<Any?>? ->
//            WindowSetup.messenger.send(
//                message
//            )
//        })
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            windowManager!!.defaultDisplay.getSize(szWindow)
        } else {
            val displaymetrics = DisplayMetrics()
            windowManager!!.defaultDisplay.getMetrics(displaymetrics)
            val w = displaymetrics.widthPixels
            val h = displaymetrics.heightPixels
            szWindow[w] = h
        }
        val params = WindowManager.LayoutParams(
            if (WindowSetup.width === -1999) -1 else WindowSetup.width,
            if (WindowSetup.height !== -1999) WindowSetup.height else screenHeight(),
            0,
            -statusBarHeightPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowSetup.flag or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag === clickableFlag) {
            params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
        }
        params.gravity = WindowSetup.gravity
        flutterView!!.setOnTouchListener(this)
        windowManager!!.addView(flutterView, params)
        return START_STICKY
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun screenHeight(): Int {
        val display = windowManager!!.defaultDisplay
        val dm = DisplayMetrics()
        display.getRealMetrics(dm)
        return if (inPortrait()) dm.heightPixels + statusBarHeightPx() + navigationBarHeightPx() else dm.heightPixels + statusBarHeightPx()
    }

    private fun statusBarHeightPx(): Int {
        if (mStatusBarHeight == -1) {
            val statusBarHeightId =
                mResources!!.getIdentifier("status_bar_height", "dimen", "android")
            if (statusBarHeightId > 0) {
                mStatusBarHeight = mResources!!.getDimensionPixelSize(statusBarHeightId)
            } else {
                mStatusBarHeight = dpToPx(DEFAULT_STATUS_BAR_HEIGHT_DP)
            }
        }
        return mStatusBarHeight
    }

    fun navigationBarHeightPx(): Int {
        if (mNavigationBarHeight == -1) {
            val navBarHeightId =
                mResources!!.getIdentifier("navigation_bar_height", "dimen", "android")
            if (navBarHeightId > 0) {
                mNavigationBarHeight = mResources!!.getDimensionPixelSize(navBarHeightId)
            } else {
                mNavigationBarHeight = dpToPx(DEFAULT_NAV_BAR_HEIGHT_DP)
            }
        }
        return mNavigationBarHeight
    }

    private fun updateOverlayFlag(result: MethodChannel.Result, flag: String) {
        if (windowManager != null) {
            WindowSetup.setFlag(flag)
            val params = flutterView!!.layoutParams as WindowManager.LayoutParams
            params.flags = (WindowSetup.flag or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag === clickableFlag) {
                params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
            } else {
                params.alpha = 1f
            }
            windowManager!!.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun resizeOverlay(width: Int, height: Int, result: MethodChannel.Result) {
        if (windowManager != null) {
            val params = flutterView!!.layoutParams as WindowManager.LayoutParams
            params.width = if ((width == -1999 || width == -1)) -1 else dpToPx(width)
            params.height = if ((height != 1999 || height != -1)) dpToPx(height) else height
            windowManager!!.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    override fun onCreate() {
        createNotificationChannel()
        val notificationIntent = Intent(this, FlutterFloatingOverlayPlugin::class.java)
        val pendingFlags: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags = PendingIntent.FLAG_IMMUTABLE
        } else {
            pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, pendingFlags
        )
        val notifyIcon = getDrawableResourceId("mipmap", "launcher")
//        val notification: Notification =
//            NotificationCompat.Builder(this, OverlayConstants.CHANNEL_ID)
//                .setContentTitle(WindowSetup.overlayTitle)
//                .setContentText(WindowSetup.overlayContent)
//                .setSmallIcon(if (notifyIcon == 0) R.drawable.notification_icon else notifyIcon)
//                .setContentIntent(pendingIntent)
//                .setVisibility(WindowSetup.notificationVisibility)
//                .build()
//        startForeground(OverlayConstants.NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                OverlayConstants.CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            assert(manager != null)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    private fun getDrawableResourceId(resType: String, name: String): Int {
        return applicationContext.resources.getIdentifier(
            String.format("ic_%s", name),
            resType,
            applicationContext.packageName
        )
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            (dp.toString() + "").toFloat(),
            mResources!!.displayMetrics
        ).toInt()
    }

    private fun inPortrait(): Boolean {
        return mResources!!.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (windowManager != null && WindowSetup.enableDrag) {
            val params = flutterView!!.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    lastX = event.rawX
                    lastY = event.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (!dragging && dx * dx + dy * dy < 25) {
                        return false
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    val xx = params.x + dx.toInt()
                    val yy = params.y + dy.toInt()
                    params.x = xx
                    params.y = yy
                    windowManager!!.updateViewLayout(flutterView, params)
                    dragging = true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastYPosition = params.y
                    if (WindowSetup.positionGravity !== "none") {
                        windowManager!!.updateViewLayout(flutterView, params)
                        mTrayTimerTask = TrayAnimationTimerTask()
                        mTrayAnimationTimer = Timer()
                        mTrayAnimationTimer!!.schedule(mTrayTimerTask, 0, 25)
                    }
                    return false
                }

                else -> return false
            }
            return false
        }
        return false
    }

    private inner class TrayAnimationTimerTask() : TimerTask() {
        var mDestX = 0
        var mDestY: Int
        var params = flutterView!!.layoutParams as WindowManager.LayoutParams

        init {
            mDestY = lastYPosition
            when (WindowSetup.positionGravity) {
                "auto" -> {
                    mDestX =
                        if ((params.x + (flutterView!!.width / 2)) <= szWindow.x / 2) 0 else szWindow.x - flutterView!!.width
//                    return
                }

                "left" -> {
                    mDestX = 0
//                    return
                }

                "right" -> {
                    mDestX = szWindow.x - flutterView!!.width
//                    return
                }

                else -> {
                    mDestX = params.x
                    mDestY = params.y
//                    return
                }
            }
        }

        override fun run() {
            mAnimationHandler.post {
                params.x = (2 * (params.x - mDestX)) / 3 + mDestX
                params.y = (2 * (params.y - mDestY)) / 3 + mDestY
                windowManager!!.updateViewLayout(flutterView, params)
                if (Math.abs(params.x - mDestX) < 2 && Math.abs(params.y - mDestY) < 2) {
                    cancel()
                    mTrayAnimationTimer!!.cancel()
                }
            }
        }
    }

    companion object {
        val INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow"
        var isRunning = false
        private val MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f
    }
}