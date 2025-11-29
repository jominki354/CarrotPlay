package android.test.settings

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.flutter.view.TextureRegistry
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.carcarlauncher.clone/launcher"
    private val TAG = "CarrotPlay"
    private lateinit var virtualDisplayManager: VirtualDisplayManager
    private lateinit var launcherApps: LauncherApps
    private lateinit var taskManager: TaskManager
    private var useSystemApi = false // 시스템 API 사용 가능 여부
    private val handler = Handler(Looper.getMainLooper())
    private val textureEntries = mutableMapOf<Int, TextureRegistry.SurfaceTextureEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 가장 먼저 방향 설정 - super.onCreate 전에!
        forceLandscapeOrientation()
        
        // Hidden API Bypass (Android 9+)
        bypassHiddenApiRestrictions()
        
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
    
    /**
     * Android 9(Pie) 이상에서 Hidden API 제한을 우회합니다.
     * 이를 통해 IActivityManager 등의 비공개 시스템 API에 접근할 수 있습니다.
     */
    private fun bypassHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val classArrayClass = Class.forName("[Ljava.lang.Class;")
            val getDeclaredMethod = Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, classArrayClass)
            
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntimeMethod = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
            
            val stringArrayClass = Class.forName("[Ljava.lang.String;")
            val setHiddenApiExemptionsMethod = getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", arrayOf(stringArrayClass)) as java.lang.reflect.Method
            
            val vmRuntime = getRuntimeMethod.invoke(null)
            setHiddenApiExemptionsMethod.invoke(vmRuntime, arrayOf("L"))
            Log.i(TAG, "Hidden API restrictions bypassed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bypass hidden API restrictions", e)
        }
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
            
            // Gesture Exclusion: 하단 및 좌우 가장자리에서 시스템 제스처 비활성화
            // Android 10+ (API 29+)
            setupGestureExclusion()
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
            
            // Gesture Exclusion for API 29
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setupGestureExclusion()
            }
        }
    }
    
    /**
     * 시스템 제스처 비활성화 영역 설정
     * 하단 120px, 좌우 가장자리 40px에서 시스템 Back 제스처 비활성화
     * 차량용 런처에서 앱 내 제스처와 충돌 방지
     */
    private fun setupGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        
        window.decorView.post {
            val decorView = window.decorView
            val width = decorView.width
            val height = decorView.height
            
            if (width <= 0 || height <= 0) {
                // 크기가 아직 결정되지 않은 경우 다시 시도
                handler.postDelayed({ setupGestureExclusion() }, 100)
                return@post
            }
            
            val exclusionRects = mutableListOf<Rect>()
            
            // 하단 120px 전체 영역 (PIP 제스처바 영역)
            exclusionRects.add(Rect(0, height - 120, width, height))
            
            // 좌측 40px 전체 영역 (Back 제스처 비활성화)
            exclusionRects.add(Rect(0, 0, 40, height))
            
            // 우측 40px 전체 영역 (Back 제스처 비활성화)
            exclusionRects.add(Rect(width - 40, 0, width, height))
            
            decorView.systemGestureExclusionRects = exclusionRects
            
            Log.d(TAG, "Gesture exclusion set: bottom=${height-120}-$height, left=0-40, right=${width-40}-$width")
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "Configuring Flutter Engine")
        
        // Initialize LauncherApps
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        
        // Initialize TaskManager (시스템 API 방식)
        taskManager = TaskManager(context)
        
        // TouchInjector 초기화 (시스템 API 터치 주입)
        val touchInjectorAvailable = TouchInjector.initialize()
        Log.i(TAG, "TouchInjector available: $touchInjectorAvailable")
        
        // 시스템 API 사용 가능 여부 확인 및 TaskStackListener 등록
        Thread {
            try {
                if (taskManager.startListening()) {
                    useSystemApi = true
                    Log.i(TAG, "System API mode enabled (TaskStackListener registered)")
                } else {
                    useSystemApi = false
                    Log.w(TAG, "System API startListening failed, falling back to Root mode")
                }
            } catch (e: Exception) {
                Log.w(TAG, "System API not available, falling back to Root mode: ${e.message}")
                useSystemApi = false
            }
            
            // Root 권한도 확인 (fallback용)
            if (!useSystemApi || !touchInjectorAvailable) {
                val hasRoot = RootUtils.requestRoot()
                Log.i(TAG, "Root permission available: $hasRoot")
            }
        }.start()
        
        // TaskManager 콜백 설정
        taskManager.onAppChanged = { displayId, packageName ->
            Log.d(TAG, "App changed on display $displayId: $packageName")
            // Flutter에 알림 (필요시)
        }
        taskManager.onAppClosed = { displayId ->
            Log.d(TAG, "App closed on display $displayId")
            // Flutter에 알림 (필요시)
        }
        
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

                        // 원본 앱과 동일한 플래그: 1289 (0x509)
                        // PUBLIC(1) | OWN_CONTENT_ONLY(8) | SUPPORTS_TOUCH(0x100) | TRUSTED(0x400)
                        // TRUSTED 플래그가 없으면 시스템이 VirtualDisplay에서 앱 실행을 거부할 수 있음
                        val flags = 1289
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
                            
                            textureEntries[displayId] = textureEntry
                            
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

                        Log.d(TAG, "Launching app: $packageName on display $displayId (useSystemApi=$useSystemApi)")

                        if (packageName != null && displayId != null) {
                            if (useSystemApi) {
                                // 시스템 API 방식 (원본 앱과 동일)
                                val success = taskManager.launchAppOnDisplay(packageName, displayId)
                                result.success(success)
                            } else {
                                // Root fallback 방식
                                launchAppInDisplay(packageName, displayId)
                                result.success(true)
                            }
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
                // ============================================
                // Touch Injection (System API 우선, Root fallback)
                // ============================================
                "injectMotionEvent" -> {
                    val displayId = call.argument<Int>("displayId") ?: 0
                    val action = call.argument<Int>("action") ?: 0
                    val x = call.argument<Double>("x") ?: 0.0
                    val y = call.argument<Double>("y") ?: 0.0
                    // Flutter int는 64비트 → Java Long으로 전달됨. Number로 받아서 변환
                    val downTime = (call.argument<Number>("downTime")?.toLong()) ?: SystemClock.uptimeMillis()
                    val eventTime = (call.argument<Number>("eventTime")?.toLong()) ?: SystemClock.uptimeMillis()
                    
                    // 실시간 터치 이벤트 - 동기 처리 (Thread 없이 즉시)
                    if (TouchInjector.isAvailable()) {
                        val success = TouchInjector.injectMotionEventFromFlutter(
                            displayId, action, x.toFloat(), y.toFloat(), downTime, eventTime
                        )
                        result.success(success)
                    } else {
                        // Root fallback - 동기 방식으로
                        result.success(false)
                    }
                }
                "injectTap" -> {
                    val displayId = call.argument<Int>("displayId") ?: 0
                    val x = call.argument<Int>("x") ?: 0
                    val y = call.argument<Int>("y") ?: 0
                    
                    Thread {
                        // System API 우선 시도
                        val success = if (TouchInjector.isAvailable()) {
                            TouchInjector.injectTap(displayId, x.toFloat(), y.toFloat())
                        } else {
                            // Root fallback
                            injectTapRoot(displayId, x, y)
                        }
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                "injectSwipe" -> {
                    val displayId = call.argument<Int>("displayId") ?: 0
                    val x1 = call.argument<Int>("x1") ?: 0
                    val y1 = call.argument<Int>("y1") ?: 0
                    val x2 = call.argument<Int>("x2") ?: 0
                    val y2 = call.argument<Int>("y2") ?: 0
                    val duration = call.argument<Int>("duration") ?: 100
                    
                    Thread {
                        // System API 우선 시도
                        val success = if (TouchInjector.isAvailable()) {
                            TouchInjector.injectSwipe(displayId, x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())
                        } else {
                            // Root fallback
                            injectSwipeRoot(displayId, x1, y1, x2, y2, duration)
                        }
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                "injectLongPress" -> {
                    val displayId = call.argument<Int>("displayId") ?: 0
                    val x = call.argument<Int>("x") ?: 0
                    val y = call.argument<Int>("y") ?: 0
                    val duration = call.argument<Int>("duration") ?: 800
                    
                    Thread {
                        val success = if (TouchInjector.isAvailable()) {
                            TouchInjector.injectLongPress(displayId, x.toFloat(), y.toFloat(), duration.toLong())
                        } else {
                            // Root fallback (swipe로 구현)
                            injectSwipeRoot(displayId, x, y, x, y, duration)
                        }
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                "sendKeyEvent" -> {
                    val displayId = call.argument<Int>("displayId") ?: 0
                    val keyCode = call.argument<Int>("keyCode") ?: 0
                    
                    Thread {
                        val success = if (TouchInjector.isAvailable()) {
                            TouchInjector.injectKeyEvent(displayId, keyCode)
                        } else {
                            sendKeyEventRoot(displayId, keyCode)
                        }
                        runOnUiThread { result.success(success) }
                    }.start()
                }
                "forceStopApp" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        Thread {
                            val success = if (useSystemApi) {
                                taskManager.forceStopApp(packageName)
                            } else {
                                forceStopApp(packageName)
                            }
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "Package name required", null)
                    }
                }
                "moveToMainDisplay" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        Thread {
                            val success = moveToMainDisplay(packageName)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "Package name required", null)
                    }
                }
                // ============================================
                // 전체화면 앱 실행 (메인 디스플레이에서 전체화면 모드)
                // ============================================
                "launchAppFullscreen" -> {
                    val packageName = call.argument<String>("packageName")
                    if (packageName != null) {
                        Thread {
                            val success = launchAppFullscreen(packageName)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "Package name required", null)
                    }
                }
                "resizeVirtualDisplay" -> {
                    val displayId = call.argument<Int>("displayId")
                    val width = call.argument<Int>("width")
                    val height = call.argument<Int>("height")
                    val density = call.argument<Int>("density")
                    
                    if (displayId != null && width != null && height != null && density != null) {
                        try {
                            Log.d(TAG, "Resizing VirtualDisplay $displayId to ${width}x$height @ ${density}dpi")
                            
                            // 1. SurfaceTexture 크기 조절
                            val textureEntry = textureEntries[displayId]
                            if (textureEntry != null) {
                                textureEntry.surfaceTexture().setDefaultBufferSize(width, height)
                            }
                            
                            // 2. VirtualDisplay 크기/DPI 조절
                            val success = virtualDisplayManager.resizeVirtualDisplay(displayId, width, height, density)
                            
                            // 3. 입력 트랜잭션 동기화 (resize 후 필수)
                            if (success && TouchInjector.isAvailable()) {
                                TouchInjector.syncAfterResize()
                            }
                            
                            result.success(success)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception resizing VirtualDisplay", e)
                            result.error("RESIZE_EXCEPTION", e.message, e.toString())
                        }
                    } else {
                        result.error("INVALID_ARGS", "displayId, width, height, density required", null)
                    }
                }
                "releaseVirtualDisplay" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        virtualDisplayManager.releaseVirtualDisplay(displayId)
                        textureEntries.remove(displayId)?.release()
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGS", "Display ID required", null)
                    }
                }
                "getTopActivity" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        try {
                            val topActivity = taskManager.getTopActivity(displayId)
                            result.success(topActivity)
                        } catch (e: Exception) {
                            result.success(null)
                        }
                    } else {
                        result.success(null)
                    }
                }
                "canGoBack" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        try {
                            val canGoBack = taskManager.canGoBack(displayId)
                            result.success(canGoBack)
                        } catch (e: Exception) {
                            // 에러 시 기본적으로 true 반환 (안전하게 허용)
                            Log.w(TAG, "canGoBack error: ${e.message}")
                            result.success(true)
                        }
                    } else {
                        result.success(true)
                    }
                }
                else -> {
                    Log.w(TAG, "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        }
        
        Log.d(TAG, "Flutter Engine configured successfully")
    }

    private fun getInstalledApps(): List<Map<String, Any?>> {
        val apps = mutableListOf<Map<String, Any?>>()
        val packageManager = context.packageManager
        val ownPackageName = context.packageName // 자기 자신 패키지명
        
        Log.d(TAG, "Getting installed apps, own package: $ownPackageName")
        
        try {
            val packages = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                // 자기 자신 패키지 제외
                if (appInfo.packageName == ownPackageName) {
                    Log.d(TAG, "Skipping own package: ${appInfo.packageName}")
                    continue
                }
                
                if (packageManager.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    // 앱 아이콘을 ByteArray로 변환
                    var iconBytes: ByteArray? = null
                    try {
                        val drawable = packageManager.getApplicationIcon(appInfo)
                        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            // AdaptiveIconDrawable 등 다른 타입 처리
                            val width = drawable.intrinsicWidth.coerceAtLeast(1)
                            val height = drawable.intrinsicHeight.coerceAtLeast(1)
                            val bmp = android.graphics.Bitmap.createBitmap(
                                width.coerceAtMost(128), 
                                height.coerceAtMost(128), 
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                        
                        // Bitmap을 PNG byte array로 변환
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        iconBytes = stream.toByteArray()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get icon for ${appInfo.packageName}: ${e.message}")
                    }
                    
                    val app = mapOf<String, Any?>(
                        "packageName" to appInfo.packageName,
                        "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                        "icon" to iconBytes
                    )
                    apps.add(app)
                }
            }
            
            Log.d(TAG, "Total apps found: ${apps.size}")
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

    // ============================================
    // Touch Injection (Root Shell - Fallback)
    // ============================================
    
    private fun injectTapRoot(displayId: Int, x: Int, y: Int): Boolean {
        Log.d(TAG, "[ROOT] Injecting tap at ($x, $y) on display $displayId")
        
        // Android 12+ 에서는 -d 옵션으로 displayId 지정 가능
        // 그 이하 버전에서는 무시됨
        val cmd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "input -d $displayId tap $x $y"
        } else {
            "input tap $x $y"
        }
        
        val result = RootUtils.executeCommand(cmd)
        if (!result.success) {
            Log.e(TAG, "Tap injection failed: ${result.error}")
        }
        return result.success
    }

    private fun injectSwipeRoot(displayId: Int, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        Log.d(TAG, "[ROOT] Injecting swipe from ($x1, $y1) to ($x2, $y2) duration=$durationMs on display $displayId")
        
        val cmd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "input -d $displayId swipe $x1 $y1 $x2 $y2 $durationMs"
        } else {
            "input swipe $x1 $y1 $x2 $y2 $durationMs"
        }
        
        val result = RootUtils.executeCommand(cmd)
        if (!result.success) {
            Log.e(TAG, "Swipe injection failed: ${result.error}")
        }
        return result.success
    }

    private fun sendKeyEventRoot(displayId: Int, keyCode: Int): Boolean {
        Log.d(TAG, "[ROOT] Sending keyevent $keyCode on display $displayId")
        
        val cmd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "input -d $displayId keyevent $keyCode"
        } else {
            "input keyevent $keyCode"
        }
        
        val result = RootUtils.executeCommand(cmd)
        if (!result.success) {
            Log.e(TAG, "Key event failed: ${result.error}")
        }
        return result.success
    }

    private fun forceStopApp(packageName: String): Boolean {
        Log.d(TAG, "Force stopping $packageName")
        
        val result = RootUtils.executeCommand("am force-stop $packageName")
        if (!result.success) {
            Log.e(TAG, "Force stop failed: ${result.error}")
        }
        return result.success
    }

    private fun moveToMainDisplay(packageName: String): Boolean {
        Log.d(TAG, "Moving $packageName to main display")
        
        // 해당 패키지의 launcher activity 가져오기
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val component = launchIntent?.component?.flattenToShortString() ?: return false
        
        // 메인 디스플레이(0)에서 앱 시작
        val cmd = "am start -n \"$component\" --display 0"
        val result = RootUtils.executeCommand(cmd)
        
        if (!result.success) {
            Log.e(TAG, "Move to main display failed: ${result.error}")
        }
        return result.success
    }
    
    /**
     * 앱을 메인 디스플레이에서 전체화면(FULLSCREEN) 모드로 실행합니다.
     * 원본 앱(z7/m.java + z7/l.java)과 동일한 방식:
     * - displayId = context.getDisplayId() (메인 디스플레이 = 0)
     * - setLaunchWindowingMode(1) = WINDOWING_MODE_FULLSCREEN
     * - TaskStackListener를 통해 setFocusedRootTask() 호출로 포커스 이동
     */
    private fun launchAppFullscreen(packageName: String): Boolean {
        Log.i(TAG, "========== launchAppFullscreen START ==========")
        Log.i(TAG, "Package: $packageName")
        
        // 메인 디스플레이 ID (원본 앱: context.getDisplayId())
        val mainDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: 0
        } else {
            0
        }
        Log.d(TAG, "Main display ID: $mainDisplayId")
        
        // 원본 앱(z7/m.java n() 메서드)과 동일한 흐름:
        // 1. 시스템 API 사용 시 TaskManager 통해 실행 + 포커스 설정
        if (useSystemApi) {
            try {
                // TaskManager에 실행 요청 (내부적으로 pendingLaunches에 저장)
                // TaskStackListener 콜백에서 setFocusedRootTask() 호출됨
                val success = taskManager.launchAppOnMainDisplayFullscreen(packageName, mainDisplayId)
                if (success) {
                    Log.i(TAG, "✓ Launched via System API + TaskManager")
                    Log.i(TAG, "========== launchAppFullscreen END (SUCCESS) ==========")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "System API launch failed: ${e.message}")
            }
        }
        
        // 2. ROOT Shell fallback (가장 신뢰성 높음)
        try {
            Log.d(TAG, "Strategy: ROOT shell launch for fullscreen")
            
            // Launcher activity 가져오기
            var componentName: android.content.ComponentName? = null
            try {
                val activities = launcherApps.getActivityList(packageName, android.os.Process.myUserHandle())
                if (activities != null && activities.isNotEmpty()) {
                    componentName = activities[0].componentName
                }
            } catch (e: Exception) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                componentName = launchIntent?.component
            }
            
            if (componentName == null) {
                Log.e(TAG, "Failed to get component name for $packageName")
                return false
            }
            
            val componentString = componentName.flattenToShortString()
            val escapedComponent = componentString.replace("$", "\\$")
            
            // am start --display 0 --windowingMode 1 (FULLSCREEN)
            // -W: wait for launch to complete (포커스 보장)
            val cmd = "am start -W -n \"$escapedComponent\" --display $mainDisplayId --windowingMode 1 " +
                      "-f ${Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP}"
            Log.i(TAG, "Executing ROOT command: $cmd")
            
            val result = RootUtils.executeCommand(cmd)
            
            if (result.success) {
                Log.i(TAG, "✓ Launched fullscreen via ROOT shell")
                Log.i(TAG, "========== launchAppFullscreen END (SUCCESS via ROOT) ==========")
                return true
            } else {
                Log.w(TAG, "✗ ROOT launch failed: ${result.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ ROOT shell launch failed", e)
        }
        
        Log.e(TAG, "========== launchAppFullscreen END (FAILED) ==========")
        return false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // TaskManager 정리
        if (::taskManager.isInitialized) {
            taskManager.destroy()
        }
        Log.d(TAG, "MainActivity onDestroy - TaskManager destroyed")
    }
}
