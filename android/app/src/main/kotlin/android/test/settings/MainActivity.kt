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
import android.widget.FrameLayout
import android.graphics.PixelFormat
import io.flutter.view.TextureRegistry
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.carcarlauncher.clone/launcher"
    private val NATIVE_PIP_CHANNEL = "com.carcarlauncher.clone/native_pip"
    private val TAG = "CarrotPlay"
    private lateinit var virtualDisplayManager: VirtualDisplayManager
    private lateinit var launcherApps: LauncherApps
    private lateinit var taskManager: TaskManager
    private var useSystemApi = false // 시스템 API 사용 가능 여부
    private val handler = Handler(Looper.getMainLooper())
    private val textureEntries = mutableMapOf<Int, TextureRegistry.SurfaceTextureEntry>()

    // Native PIP Manager (3개의 별도 Window 관리)
    private var nativePipManager: NativePipManager? = null
    // Legacy: 이전 방식 (제거 예정)
    private var nativePipContainer: NativePipContainer? = null
    private var flutterContainer: FrameLayout? = null
    private var pipContainer: FrameLayout? = null

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
        
        // Layout 변경 감지하여 제스처 제외 영역 업데이트 (키보드 등)
        window.decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            setupGestureExclusion()
        }
        
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
    
    /**
     * 원본 앱 방식: AndroidTouchProcessor.trackMotionEvents = true 설정
     * 이렇게 하면 Flutter가 원본 MotionEvent를 저장하고, 나중에 ID로 검색 가능
     * 
     * 원본 코드 (o1/b.java):
     * Field declaredField2 = s3.a.class.getDeclaredField("trackMotionEvents");
     * declaredField2.setAccessible(true);
     * declaredField2.set(aVar3, Boolean.TRUE);
     * 
     * 추가: MotionEventTracker 인스턴스를 가져와서 PlatformViewTouchHandler에 전달
     */
    private fun enableTrackMotionEvents(flutterEngine: FlutterEngine) {
        try {
            // FlutterView 가져오기
            val flutterView = flutterEngine.renderer
            
            // io.flutter.embedding.android.AndroidTouchProcessor 클래스
            val touchProcessorClass = Class.forName("io.flutter.embedding.android.AndroidTouchProcessor")
            
            // trackMotionEvents 필드 찾기
            val trackMotionEventsField = touchProcessorClass.getDeclaredField("trackMotionEvents")
            trackMotionEventsField.isAccessible = true
            
            // motionEventTracker 필드 찾기 (원본 앱 방식)
            val motionEventTrackerField = touchProcessorClass.getDeclaredField("motionEventTracker")
            motionEventTrackerField.isAccessible = true
            
            // FlutterView에서 AndroidTouchProcessor 인스턴스 가져오기
            // FlutterView 클래스에서 androidTouchProcessor 필드 찾기
            val flutterViewClass = Class.forName("io.flutter.embedding.android.FlutterView")
            val touchProcessorField = flutterViewClass.getDeclaredField("androidTouchProcessor")
            touchProcessorField.isAccessible = true
            
            // 현재 Activity의 FlutterView 찾기 (지연 실행 필요)
            handler.postDelayed({
                try {
                    val decorView = window.decorView
                    val flutterViewInstance = findFlutterView(decorView)
                    
                    if (flutterViewInstance != null) {
                        val touchProcessor = touchProcessorField.get(flutterViewInstance)
                        if (touchProcessor != null) {
                            // trackMotionEvents = true 설정
                            trackMotionEventsField.set(touchProcessor, true)
                            Log.i(TAG, "AndroidTouchProcessor.trackMotionEvents = true (원본 앱 방식)")
                            
                            // MotionEventTracker 인스턴스 가져오기
                            val motionEventTracker = motionEventTrackerField.get(touchProcessor)
                            if (motionEventTracker != null) {
                                // PlatformViewTouchHandler 초기화
                                val initialized = PlatformViewTouchHandler.initialize(motionEventTracker)
                                Log.i(TAG, "PlatformViewTouchHandler initialized: $initialized")
                            } else {
                                Log.w(TAG, "MotionEventTracker instance is null")
                                // MotionEventTracker 없이도 초기화 시도
                                PlatformViewTouchHandler.initialize(null)
                            }
                        } else {
                            Log.w(TAG, "AndroidTouchProcessor instance is null")
                        }
                    } else {
                        Log.w(TAG, "FlutterView not found in view hierarchy")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set trackMotionEvents (delayed)", e)
                }
            }, 1000) // FlutterView가 생성될 때까지 대기
            
            Log.d(TAG, "enableTrackMotionEvents scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable trackMotionEvents", e)
        }
    }
    
    /**
     * View 계층에서 FlutterView 찾기
     */
    private fun findFlutterView(view: android.view.View): Any? {
        try {
            val flutterViewClass = Class.forName("io.flutter.embedding.android.FlutterView")
            if (flutterViewClass.isInstance(view)) {
                return view
            }
            
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val result = findFlutterView(view.getChildAt(i))
                    if (result != null) return result
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding FlutterView", e)
        }
        return null
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
     * Android 10+ 제스처 제외 영역 제한: 수직 엣지당 최대 200dp
     * 전체 높이를 제외하려고 하면 시스템이 요청을 무시할 수 있음
     * 따라서 하단부(앱 제스처 영역 + 키보드 영역 일부)를 우선적으로 제외
     */
    private fun setupGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        
        window.decorView.post {
            val decorView = window.decorView
            val width = decorView.width
            val height = decorView.height
            
            if (width <= 0 || height <= 0) return@post
            
            val exclusionRects = mutableListOf<Rect>()
            
            val density = resources.displayMetrics.density
            // 200dp 제한 (시스템 한계)
            val maxExclusionHeight = (200 * density).toInt()
            
            // 하단 180dp 제외 (앱 제스처바 및 키보드 하단 보호)
            // 키보드가 올라왔을 때도 하단부의 Back 제스처 오동작 방지
            val bottomExclusionHeight = (180 * density).toInt().coerceAtMost(maxExclusionHeight)
            
            // 하단 영역 전체 너비 제외 (좌우 엣지의 하단부 포함)
            exclusionRects.add(Rect(0, height - bottomExclusionHeight, width, height))
            
            decorView.systemGestureExclusionRects = exclusionRects
            
            Log.d(TAG, "Gesture exclusion updated: bottom ${bottomExclusionHeight}px")
        }
    }

    // PlatformView Factory 참조 (dispose를 위해)
    private var virtualDisplayViewFactory: VirtualDisplayViewFactory? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "Configuring Flutter Engine")
        
        // 원본 앱 방식: AndroidTouchProcessor.trackMotionEvents = true 설정
        // 이렇게 하면 원본 MotionEvent가 저장되어 나중에 ID로 검색 가능
        enableTrackMotionEvents(flutterEngine)
        
        // Initialize LauncherApps
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        
        // Initialize TaskManager (시스템 API 방식)
        taskManager = TaskManager(context)
        
        // TouchInjector 초기화 (시스템 API 터치 주입)
        val touchInjectorAvailable = TouchInjector.initialize()
        Log.i(TAG, "TouchInjector available: $touchInjectorAvailable")
        
        // VirtualDisplay PlatformView 등록 (원본 앱 방식 터치 처리)
        virtualDisplayViewFactory = VirtualDisplayViewFactory(flutterEngine.dartExecutor.binaryMessenger)
        flutterEngine.platformViewsController.registry.registerViewFactory(
            VirtualDisplayViewFactory.VIEW_TYPE,
            virtualDisplayViewFactory!!
        )
        Log.i(TAG, "VirtualDisplayViewFactory registered: ${VirtualDisplayViewFactory.VIEW_TYPE}")
        
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
            
            // Root 권한도 확인 (fallback용) + 지속 세션 시작
            if (!useSystemApi || !touchInjectorAvailable) {
                val hasRoot = RootUtils.requestRoot()
                Log.i(TAG, "Root permission available: $hasRoot")
                if (hasRoot) {
                    // 지속적인 su 세션 시작 (프리셋 전환 최적화)
                    RootUtils.initPersistentSession()
                }
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
        
        virtualDisplayManager = VirtualDisplayManager.getInstance(context)

        // =================================================================================
        // Native PIP Channel Setup
        // =================================================================================
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NATIVE_PIP_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "setEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    if (enabled) {
                        val screenWidth = call.argument<Int>("screenWidth") ?: 1920
                        val screenHeight = call.argument<Int>("screenHeight") ?: 1080
                        val pipHeight = call.argument<Int>("pipHeight") ?: screenHeight
                        Log.i(TAG, "Native PIP setEnabled: true, screen=${screenWidth}x${screenHeight}, pipHeight=$pipHeight")
                        
                        // 동기적으로 초기화 (runOnUiThread 내부에서 완료 후 result 반환)
                        runOnUiThread {
                            try {
                                if (nativePipManager == null) {
                                    // WindowManager를 통해 3개의 별도 Window로 추가
                                    val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                                    val windowToken = window.decorView.windowToken
                                    
                                    // Dock 너비 (Flutter에서 72dp = 72 * density)
                                    val density = resources.displayMetrics.density
                                    val dockWidthPx = (72 * density).toInt()
                                    
                                    // NativePipManager 생성 (3개의 Window 관리)
                                    val pipWidth = screenWidth - dockWidthPx
                                    nativePipManager = NativePipManager(context, wm, windowToken)
                                    
                                    // 초기화 (3개의 Window 생성)
                                    val success = nativePipManager?.initialize(
                                        x = dockWidthPx,
                                        y = 0,
                                        width = pipWidth,
                                        height = pipHeight
                                    ) ?: false
                                    
                                    if (success) {
                                        // Setup callbacks
                                        nativePipManager?.onRatioChanged = { ratio ->
                                            Log.d(TAG, "Native PIP ratio changed: $ratio")
                                        }
                                        
                                        Log.i(TAG, "Native PIP Manager created: ${pipWidth}x${pipHeight}, dockWidth=$dockWidthPx (3 separate Windows)")
                                        result.success(true)
                                    } else {
                                        Log.e(TAG, "Native PIP Manager initialization failed")
                                        nativePipManager?.release()
                                        nativePipManager = null
                                        result.success(false)
                                    }
                                } else {
                                    nativePipManager?.setVisible(true)
                                    Log.i(TAG, "Native PIP Manager already initialized, making visible")
                                    result.success(true)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Native PIP initialization failed", e)
                                result.success(false)
                            }
                        }
                    } else {
                        Log.i(TAG, "Native PIP setEnabled: false")
                        // 비활성화 시 Native PIP 숨김
                        runOnUiThread {
                            nativePipManager?.setVisible(false)
                        }
                        result.success(true)
                    }
                }
                "initNativePip" -> {
                    val width = call.argument<Int>("width") ?: 800
                    val height = call.argument<Int>("height") ?: 480
                    setupNativeLayout(width, height)
                    result.success(true)
                }
                "updatePipRatio" -> {
                    val ratio = call.argument<Double>("ratio")?.toFloat() ?: 0.5f
                    nativePipManager?.setRatio(ratio)
                    result.success(true)
                }
                "setRatio" -> {
                    val ratio = call.argument<Double>("ratio")?.toFloat() ?: 0.5f
                    nativePipManager?.setRatio(ratio)
                    result.success(true)
                }
                "setRatioAnimated" -> {
                    val ratio = call.argument<Double>("ratio")?.toFloat() ?: 0.5f
                    val durationMs = call.argument<Int>("durationMs")?.toLong() ?: 150L
                    nativePipManager?.setRatioAnimated(ratio, durationMs) {
                        // 애니메이션 완료 콜백 (필요시 Flutter로 전달)
                    }
                    result.success(true)
                }
                "launchAppInPip", "launchApp" -> {
                    val pipIndex = call.argument<Int>("pipIndex") ?: 0
                    val packageName = call.argument<String>("packageName")
                    Log.i(TAG, "launchApp: pipIndex=$pipIndex, packageName=$packageName, nativePipManager=${nativePipManager != null}")
                    if (packageName != null) {
                        val success = nativePipManager?.launchApp(pipIndex, packageName) ?: false
                        Log.i(TAG, "launchApp result: $success")
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGS", "Package name required", null)
                    }
                }
                "getPipInfo" -> {
                    val pipIndex = call.argument<Int>("pipIndex") ?: 0
                    val info = mapOf(
                        "displayId" to (nativePipManager?.getPipDisplayId(pipIndex) ?: -1),
                        "currentPackage" to (nativePipManager?.getPipCurrentPackage(pipIndex))
                    )
                    result.success(info)
                }
                else -> result.notImplemented()
            }
        }

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
                    
                    // 원본 앱 방식: PointerEvent의 모든 속성 전달
                    val device = call.argument<Int>("device") ?: 0
                    val pressure = call.argument<Double>("pressure")?.toFloat() ?: 1.0f
                    val size = call.argument<Double>("size")?.toFloat() ?: 1.0f
                    val source = call.argument<Int>("source") ?: android.view.InputDevice.SOURCE_TOUCHSCREEN
                    val toolType = call.argument<Int>("toolType") ?: android.view.MotionEvent.TOOL_TYPE_FINGER
                    val pointerId = call.argument<Int>("pointerId") ?: 0
                    
                    // 실시간 터치 이벤트 - 동기 처리 (Thread 없이 즉시)
                    if (TouchInjector.isAvailable()) {
                        val success = TouchInjector.injectMotionEventFromFlutter(
                            displayId, action, x.toFloat(), y.toFloat(), downTime, eventTime,
                            device, pressure, size, source, toolType, pointerId
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
                "moveTaskToBack" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        Thread {
                            val success = taskManager.moveTaskToBack(displayId)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "displayId required", null)
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
                "clearDisplayTasks" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        Thread {
                            val success = taskManager.clearDisplayTasks(displayId)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "displayId required", null)
                    }
                }
                "removeVisibleTaskOnDisplay" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        Thread {
                            val success = taskManager.removeVisibleTaskOnDisplay(displayId)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "displayId required", null)
                    }
                }
                "sendTaskToBackground" -> {
                    val displayId = call.argument<Int>("displayId")
                    if (displayId != null) {
                        Thread {
                            val success = taskManager.sendTaskToBackground(displayId)
                            runOnUiThread { result.success(success) }
                        }.start()
                    } else {
                        result.error("INVALID_ARGS", "displayId required", null)
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

    private fun setupNativeLayout(width: Int, height: Int) {
        runOnUiThread {
            if (nativePipContainer != null) return@runOnUiThread

            flutterContainer = findViewById(R.id.flutter_container)
            pipContainer = findViewById(R.id.native_pip_container)

            if (pipContainer == null) {
                Log.e(TAG, "Native PIP container view not found in XML!")
                return@runOnUiThread
            }

            // Create NativePipContainer
            nativePipContainer = NativePipContainer(context, width, height)
            
            // Add to layout
            pipContainer?.removeAllViews()
            pipContainer?.addView(nativePipContainer)
            pipContainer?.visibility = View.VISIBLE // Make sure it's visible!

            // Setup callbacks
            nativePipContainer?.onRatioChanged = { ratio ->
                // Send back to Flutter if needed
                // MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, NATIVE_PIP_CHANNEL).invokeMethod("onRatioChanged", mapOf("ratio" to ratio))
            }
            
            Log.i(TAG, "Native PIP Layout initialized with size ${width}x${height}")
        }
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
        
        // Strategy 1: ROOT Shell (Most Reliable) - 비동기 실행으로 최적화
        try {
            Log.d(TAG, "Strategy 1: Attempting ROOT shell launch (async)")
            
            // Escape special characters for shell
            val escapedComponent = componentString.replace("$", "\\$")
            
            // Command: am start -n <Component> --display <DisplayId> --windowingMode 1
            // --activity-single-top: 기존 인스턴스 재사용
            // --activity-clear-top: 위의 Activity들 제거
            // --activity-reorder-to-front: 기존 Task를 앞으로
            val cmd = "am start -n \"$escapedComponent\" --display $displayId --windowingMode 1 --activity-single-top --activity-clear-top --activity-reorder-to-front"
            Log.i(TAG, "Executing ROOT command (async): $cmd")
            
            // 비동기 실행 (결과 대기 안 함 - 프리셋 전환 최적화)
            RootUtils.executeCommandAsync(cmd)
            
            Log.i(TAG, "✓ ROOT command queued for async execution")
            Log.i(TAG, "========== launchAppInDisplay END (ASYNC ROOT) ==========")
            return
            
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
                // 기존 인스턴스 재사용
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
        
        // Native PIP Manager 정리 (3개 Window 모두 제거)
        nativePipManager?.release()
        nativePipManager = null
        Log.d(TAG, "Native PIP manager released")
        
        // TaskManager 정리
        if (::taskManager.isInitialized) {
            taskManager.destroy()
        }
        Log.d(TAG, "MainActivity onDestroy - TaskManager destroyed")
    }
}
