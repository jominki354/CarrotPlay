package com.example.carcar_launcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.carcarlauncher.clone/launcher"
    private val TAG = "CarrotPlay"
    private lateinit var virtualDisplayManager: VirtualDisplayManager
    private lateinit var launcherApps: LauncherApps
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // 가장 먼저 방향 설정 - super.onCreate 전에!
        forceLandscapeOrientation()
        
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen immersive mode
        enableFullscreenMode()
        
        // Keep screen on (차량용)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 추가 지연 설정 (Flutter 엔진 초기화 후)
        handler.postDelayed({ 
            forceLandscapeOrientation()
            enableFullscreenMode()
        }, 500)
        
        Log.d(TAG, "MainActivity onCreate - Landscape mode enforced, orientation=${requestedOrientation}")
        @Suppress("DEPRECATION")
        Log.d(TAG, "Display rotation: ${getSystemService(WINDOW_SERVICE).let { (it as WindowManager).defaultDisplay.rotation }}")
    }
    
    private fun forceLandscapeOrientation() {
        // 여러 방법으로 강제 가로모드 설정
        try {
            // Method 1: Direct requestedOrientation - 강제 가로 (센서 무관)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Log.d(TAG, "Set LANDSCAPE (requestedOrientation)")
            
            // Method 2: Window attributes (additional enforcement)
            val params = window.attributes
            // Some devices respect this flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.attributes = params
            }
            
            Log.d(TAG, "Current orientation after setting: ${requestedOrientation}")
            Log.d(TAG, "Resources config orientation: ${resources.configuration.orientation}")
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing landscape orientation", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 매 resume 시 가로모드 및 전체화면 재적용
        forceLandscapeOrientation()
        enableFullscreenMode()
        
        // 지연 재적용 (시스템이 override 할 경우 대비)
        handler.postDelayed({
            forceLandscapeOrientation()
            enableFullscreenMode()
            Log.d(TAG, "MainActivity onResume delayed - Landscape re-enforced")
        }, 200)
        
        Log.d(TAG, "MainActivity onResume - Landscape mode re-enforced")
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreenMode()
            forceLandscapeOrientation()
            Log.d(TAG, "onWindowFocusChanged(hasFocus=true) - orientation re-applied")
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: orientation=${newConfig.orientation}")
        
        // 세로 모드로 바뀌면 즉시 가로로 재설정
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.w(TAG, "Portrait detected! Forcing back to landscape...")
            handler.post { forceLandscapeOrientation() }
        }
    }
    
    private fun enableFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "Configuring Flutter Engine")
        
        // Initialize LauncherApps
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        
        // Request Root Permission on startup
        Thread {
            val hasRoot = RootUtils.requestRoot()
            Log.i(TAG, "Root permission available: $hasRoot")
        }.start()
        
        virtualDisplayManager = VirtualDisplayManager(context)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            Log.d(TAG, "MethodChannel received: ${call.method}")
            
            when (call.method) {
                "createVirtualDisplay" -> {
                    try {
                        val width = call.argument<Int>("width") ?: 800
                        val height = call.argument<Int>("height") ?: 600
                        val density = call.argument<Int>("density") ?: 160
                        
                        Log.d(TAG, "Creating VirtualDisplay: ${width}x$height @ ${density}dpi")
                        
                        val textureEntry = flutterEngine.renderer.createSurfaceTexture()
                        val surfaceTexture = textureEntry.surfaceTexture()
                        surfaceTexture.setDefaultBufferSize(width, height)
                        val surface = Surface(surfaceTexture)

                        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or 
                                   DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        val virtualDisplay = virtualDisplayManager.createVirtualDisplay(
                            "CarCarVD-${System.currentTimeMillis()}",
                            width,
                            height,
                            density,
                            surface,
                            flags
                        )

                        if (virtualDisplay != null) {
                            val displayId = virtualDisplay.display.displayId
                            val textureId = textureEntry.id()
                            
                            Log.d(TAG, "VirtualDisplay created: texture=$textureId, display=$displayId")
                            
                            result.success(mapOf(
                                "textureId" to textureId,
                                "displayId" to displayId
                            ))
                        } else {
                            Log.e(TAG, "Failed to create VirtualDisplay")
                            result.error("VD_ERROR", "Failed to create Virtual Display", null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception creating VirtualDisplay", e)
                        result.error("VD_EXCEPTION", e.message, e.toString())
                    }
                }
                "launchApp" -> {
                    try {
                        val packageName = call.argument<String>("packageName")
                        val displayId = call.argument<Int>("displayId")

                        Log.d(TAG, "Launching app: $packageName on display $displayId")

                        if (packageName != null && displayId != null) {
                            launchAppInDisplay(packageName, displayId)
                            result.success(true)
                        } else {
                            Log.e(TAG, "Missing package name or display ID")
                            result.error("INVALID_ARGS", "Package name or display ID missing", null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception launching app", e)
                        result.error("LAUNCH_EXCEPTION", e.message, e.toString())
                    }
                }
                "getInstalledApps" -> {
                    try {
                        val apps = getInstalledApps()
                        result.success(apps)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception getting installed apps", e)
                        result.error("APPS_EXCEPTION", e.message, e.toString())
                    }
                }
                "checkRoot" -> {
                    Thread {
                        val hasRoot = RootUtils.isRootAvailable()
                        runOnUiThread { result.success(hasRoot) }
                    }.start()
                }
                "installAsSystemApp" -> {
                    Thread {
                        val success = installAsSystemApp()
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                else -> {
                    Log.w(TAG, "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        }
        
        Log.d(TAG, "Flutter Engine configured successfully")
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val apps = mutableListOf<Map<String, Any>>()
        val packageManager = context.packageManager
        
        try {
            val packages = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                if (packageManager.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    val app = mapOf(
                        "packageName" to appInfo.packageName,
                        "appName" to packageManager.getApplicationLabel(appInfo).toString()
                    )
                    apps.add(app)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
            throw e
        }
        
        return apps
    }

    private fun launchAppInDisplay(packageName: String, displayId: Int) {
        Log.i(TAG, "========== launchAppInDisplay START ==========")
        Log.i(TAG, "Package: $packageName, Display: $displayId")
        
        // Get the correct launcher activity using LauncherApps API
        var componentName: android.content.ComponentName? = null
        try {
            val activities = launcherApps.getActivityList(packageName, android.os.Process.myUserHandle())
            if (activities != null && activities.isNotEmpty()) {
                componentName = activities[0].componentName
                Log.d(TAG, "LauncherApps found component: ${componentName.flattenToShortString()}")
            } else {
                Log.w(TAG, "No launcher activities found for $packageName")
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                componentName = launchIntent?.component
                Log.d(TAG, "PackageManager fallback component: ${componentName?.flattenToShortString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launcher activity", e)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            componentName = launchIntent?.component
        }
        
        if (componentName == null) {
            Log.e(TAG, "Failed to get component name for $packageName")
            return
        }
        
        val componentString = componentName.flattenToShortString()
        
        // Strategy 1: ROOT Shell (Most Reliable)
        try {
            Log.d(TAG, "Strategy 1: Attempting ROOT shell launch")
            Log.d(TAG, "Root command target: $componentString")
            
            // Escape special characters for shell
            val escapedComponent = componentString.replace("$", "\\$")
            
            // Command: am start -n <Component> --display <DisplayId> --windowingMode 1
            val cmd = "am start -n \"$escapedComponent\" --display $displayId --windowingMode 1"
            Log.i(TAG, "Executing ROOT command: $cmd")
            
            val result = RootUtils.executeCommand(cmd)
            
            Log.d(TAG, "Root command exit code: ${if(result.success) "0 (success)" else "non-zero (failed)"}")
            Log.d(TAG, "Root command output: ${result.output}")
            if (result.error.isNotEmpty()) {
                Log.w(TAG, "Root command error: ${result.error}")
            }
            
            if (result.success) {
                Log.i(TAG, "✓ Launched via ROOT shell successfully")
                Log.i(TAG, "========== launchAppInDisplay END (SUCCESS via ROOT) ==========")
                return
            } else {
                Log.w(TAG, "✗ ROOT launch failed, trying PendingIntent fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ ROOT shell launch failed", e)
        }
        
        // Strategy 2: PendingIntent (Fallback)
        try {
            Log.d(TAG, "Strategy 2: Attempting PendingIntent launch (Fallback)")
            
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(componentName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            
            // setLaunchWindowingMode via reflection (hidden API)
            // Wrapped in try-catch for graceful fallback on newer Android versions
            @Suppress("DiscouragedPrivateApi")
            try {
                val setWindowingModeMethod = ActivityOptions::class.java.getDeclaredMethod(
                    "setLaunchWindowingMode", 
                    Int::class.javaPrimitiveType
                )
                setWindowingModeMethod.invoke(options, 1)
                Log.d(TAG, "✓ setLaunchWindowingMode(1) called successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set windowing mode: ${e.message}")
                Log.w(TAG, "⚠️ App may launch on main display!")
            }
            
            Log.d(TAG, "ActivityOptions.launchDisplayId set to: $displayId")
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent,
                201326592 // FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE
            )
            
            pendingIntent.send(
                null,
                0, 
                null, 
                null, 
                null, 
                null,
                options.toBundle()
            )
            Log.i(TAG, "✓ PendingIntent.send() completed successfully")
            Log.i(TAG, "========== launchAppInDisplay END (SUCCESS via PendingIntent) ==========")
        } catch (e: Exception) {
            Log.e(TAG, "✗ All launch strategies failed", e)
            Log.i(TAG, "========== launchAppInDisplay END (FAILED) ==========")
            throw e
        }
    }

    private fun installAsSystemApp(): Boolean {
        try {
            val apkPath = context.applicationInfo.sourceDir
            Log.d(TAG, "Current APK path: $apkPath")
            
            val targetDir = "/system/priv-app/CarrotPlay"
            val targetApk = "$targetDir/CarrotPlay.apk"
            
            val commands = listOf(
                "mount -o rw,remount /system",
                "mkdir -p $targetDir",
                "cp $apkPath $targetApk",
                "chmod 644 $targetApk",
                "chown root:root $targetApk",
                "chcon u:object_r:system_file:s0 $targetApk",
                "mount -o ro,remount /system"
            )
            
            for (cmd in commands) {
                val result = RootUtils.executeCommand(cmd)
                if (!result.success) {
                    Log.e(TAG, "Failed command: $cmd -> ${result.error}")
                    return false
                }
            }
            
            Log.d(TAG, "System app installation successful. Reboot required.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception installing system app", e)
            return false
        }
    }
}
