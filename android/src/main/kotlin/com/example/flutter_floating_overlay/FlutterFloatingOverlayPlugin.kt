package com.example.flutter_floating_overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.flutter_overlay_example.OverlayConstants
import com.example.flutter_overlay_example.WindowSetup
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/** FlutterFloatingOverlayPlugin */
class FlutterFloatingOverlayPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    ActivityResultListener {
    private val videoUrl: String = "assets/video.mp4"
    private lateinit var context: Context
    private lateinit var mActivity: Activity
    private lateinit var channel: MethodChannel
    private lateinit var pendingResult: MethodChannel.Result
    private val REQUEST_CODE_FOR_OVERLAY_PERMISSION = 1248

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_FOR_OVERLAY_PERMISSION) {
            pendingResult.success(checkOverlayPermission())
            return true
        }
        return false
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel =
            MethodChannel(binding.binaryMessenger, OverlayConstants.CHANNEL_TAG)
        channel.setMethodCallHandler(this)

//        messenger = BasicMessageChannel<Any?>(
//            flutterPluginBinding.getBinaryMessenger(), OverlayConstants.MESSENGER_TAG,
//            JSONMessageCodec.INSTANCE
//        )
//        messenger.setMessageHandler(this)

//        WindowSetup.messenger = messenger
//        WindowSetup.messenger!!.setMessageHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
//        WindowSetup.messenger!!.setMessageHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        val enn = FlutterEngineGroup(context)
        val dEntry = DartEntrypoint(
            FlutterInjector.instance().flutterLoader().findAppBundlePath(),
            "overlayMain"
        )
        val engine = enn.createAndRunEngine(context, dEntry)
        FlutterEngineCache.getInstance().put(OverlayConstants.CACHED_TAG, engine)
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        mActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getVideoUrl" -> {
                result.success(videoUrl)
            }
            "checkPermission" -> {
                result.success(checkOverlayPermission())
            }

            "isOverlayActive" -> {
                result.success(OverlayService.isRunning)
                return
            }

            "requestPermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:" + mActivity.packageName)
                    mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_OVERLAY_PERMISSION)
                } else {
                    result.success(true)
                }
            }

            "showOverlay" -> {
                if (!checkOverlayPermission()) {
                    result.error("PERMISSION", "overlay permission is not enabled", null)
                    return
                }
                val height = call.argument<Int>("height")
                val width = call.argument<Int>("width")
                val alignment = call.argument<String>("alignment")
                val flag = call.argument<String>("flag")
                val overlayTitle = call.argument<String>("overlayTitle")
                val overlayContent = call.argument<String>("overlayContent")
                val notificationVisibility = call.argument<String>("notificationVisibility")
                val enableDrag = call.argument<Boolean>("enableDrag")!!
                val positionGravity = call.argument<String>("positionGravity")

                WindowSetup.width = width ?: -1
                WindowSetup.height = height ?: -1
                WindowSetup.enableDrag = enableDrag
                WindowSetup.setGravityFromAlignment(alignment ?: "center")
                WindowSetup.setFlag(flag ?: "flagNotFocusable")
                WindowSetup.overlayTitle = overlayTitle ?: ""
                WindowSetup.overlayContent = overlayContent ?: ""
                WindowSetup.positionGravity = positionGravity ?: ""
                WindowSetup.setNotificationVisibility(notificationVisibility ?: "")

                val intent = Intent(context, OverlayService::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startService(intent)
                result.success(null)
            }

            "closeOverlay" -> {
                if (OverlayService.isRunning) {
                    val i = Intent(context, OverlayService::class.java)
                    i.putExtra(OverlayService.INTENT_EXTRA_IS_CLOSE_WINDOW, true)
                    context.startService(i)
                    result.success(true)
                }
                return
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }
}
