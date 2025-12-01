package android.test.settings

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator

/**
 * NativePipManager - 3개의 별도 Window를 관리하는 매니저
 * 
 * 구조:
 * ┌─ PIP1 Window ─┐  ┌─ Divider ─┐  ┌─ PIP2 Window ─┐
 * │  SurfaceView  │  │   View    │  │  SurfaceView  │
 * └───────────────┘  └───────────┘  └───────────────┘
 * 
 * 각 Window가 독립적이므로 SurfaceView Z-order 충돌 없음
 */
class NativePipManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val windowToken: android.os.IBinder
) {
    companion object {
        private const val TAG = "NativePipManager"
        private const val DIVIDER_WIDTH = 8
        private const val MIN_RATIO = 0.25f
        private const val MAX_RATIO = 0.75f
        
        private var lastPerfLogTime = 0L
    }
    
    // PIP Windows
    private var pip1Window: NativePipWindow? = null
    private var pip2Window: NativePipWindow? = null
    private var dividerWindow: NativePipDivider? = null
    
    // 컨테이너 영역 (Dock 오른쪽)
    private var containerX = 0
    private var containerY = 0
    private var containerWidth = 0
    private var containerHeight = 0
    
    // 현재 비율 (왼쪽 PIP 비율)
    private var leftRatio = 0.5f
    
    // 콜백
    var onRatioChanged: ((Float) -> Unit)? = null
    
    // 애니메이션
    private var ratioAnimator: ValueAnimator? = null
    
    /**
     * 초기화 및 Window 생성
     */
    fun initialize(x: Int, y: Int, width: Int, height: Int): Boolean {
        containerX = x
        containerY = y
        containerWidth = width
        containerHeight = height
        
        Log.i(TAG, "Initializing NativePipManager: pos=($x,$y) size=${width}x${height}")
        
        try {
            // PIP1 Window (왼쪽)
            pip1Window = NativePipWindow(context, 0)
            
            // PIP2 Window (오른쪽)
            pip2Window = NativePipWindow(context, 1)
            
            // Divider Window (구분선)
            dividerWindow = NativePipDivider(context)
            dividerWindow?.onDragStart = { startRatio ->
                // 드래그 시작
            }
            dividerWindow?.onDrag = { deltaRatio ->
                val newRatio = (leftRatio + deltaRatio).coerceIn(MIN_RATIO, MAX_RATIO)
                val snappedRatio = (newRatio * 20).toInt() / 20f
                if (snappedRatio != leftRatio) {
                    leftRatio = snappedRatio
                    updateWindowPositions()
                }
            }
            dividerWindow?.onDragEnd = {
                onRatioChanged?.invoke(leftRatio)
            }
            
            // Window 추가
            addWindows()
            
            // 초기 위치 설정
            updateWindowPositions()
            
            Log.i(TAG, "NativePipManager initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NativePipManager", e)
            release()
            return false
        }
    }
    
    /**
     * 3개의 Window를 WindowManager에 추가
     */
    private fun addWindows() {
        val availableWidth = containerWidth - DIVIDER_WIDTH
        val pip1Width = (availableWidth * leftRatio).toInt()
        val pip2Width = availableWidth - pip1Width
        
        // PIP1 Window
        pip1Window?.let { pip1 ->
            val params = createWindowParams(pip1Width, containerHeight)
            params.x = containerX
            params.y = containerY
            windowManager.addView(pip1, params)
            Log.d(TAG, "PIP1 Window added: x=${params.x}, width=$pip1Width")
        }
        
        // Divider Window
        dividerWindow?.let { divider ->
            val params = createWindowParams(DIVIDER_WIDTH, containerHeight)
            params.x = containerX + pip1Width
            params.y = containerY
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.addView(divider, params)
            divider.setContainerInfo(containerWidth, MIN_RATIO, MAX_RATIO)
            Log.d(TAG, "Divider Window added: x=${params.x}")
        }
        
        // PIP2 Window
        pip2Window?.let { pip2 ->
            val params = createWindowParams(pip2Width, containerHeight)
            params.x = containerX + pip1Width + DIVIDER_WIDTH
            params.y = containerY
            windowManager.addView(pip2, params)
            Log.d(TAG, "PIP2 Window added: x=${params.x}, width=$pip2Width")
        }
    }
    
    /**
     * WindowManager.LayoutParams 생성
     */
    private fun createWindowParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            token = windowToken
        }
    }
    
    /**
     * 비율에 따라 3개 Window 위치/크기 업데이트
     */
    private fun updateWindowPositions() {
        val startTime = System.nanoTime()
        
        val availableWidth = containerWidth - DIVIDER_WIDTH
        val pip1Width = (availableWidth * leftRatio).toInt()
        val pip2Width = availableWidth - pip1Width
        
        // PIP1 업데이트
        pip1Window?.let { pip1 ->
            val params = pip1.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.x = containerX
                params.width = pip1Width
                windowManager.updateViewLayout(pip1, params)
            }
        }
        
        // Divider 업데이트
        dividerWindow?.let { divider ->
            val params = divider.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.x = containerX + pip1Width
                windowManager.updateViewLayout(divider, params)
            }
        }
        
        // PIP2 업데이트
        pip2Window?.let { pip2 ->
            val params = pip2.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.x = containerX + pip1Width + DIVIDER_WIDTH
                params.width = pip2Width
                windowManager.updateViewLayout(pip2, params)
            }
        }
        
        val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
        
        // 성능 로그 (100ms마다)
        val now = System.currentTimeMillis()
        if (now - lastPerfLogTime > 100) {
            Log.d(TAG, "[PERF] updateWindowPositions: ratio=$leftRatio, pip1=$pip1Width, pip2=$pip2Width, elapsed=${"%.2f".format(elapsed)}ms")
            lastPerfLogTime = now
        }
    }
    
    /**
     * 비율 설정 (즉시)
     */
    fun setRatio(ratio: Float) {
        leftRatio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        updateWindowPositions()
    }
    
    /**
     * 비율 설정 (애니메이션)
     */
    fun setRatioAnimated(ratio: Float, durationMs: Long = 150, onComplete: (() -> Unit)? = null) {
        val targetRatio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        
        if (kotlin.math.abs(leftRatio - targetRatio) < 0.01f) {
            onComplete?.invoke()
            return
        }
        
        ratioAnimator?.cancel()
        
        val startRatio = leftRatio
        Log.i(TAG, "[PERF] Animation START: $startRatio -> $targetRatio")
        
        ratioAnimator = ValueAnimator.ofFloat(startRatio, targetRatio).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            
            addUpdateListener { animation ->
                leftRatio = animation.animatedValue as Float
                updateWindowPositions()
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    Log.i(TAG, "[PERF] Animation END: ratio=$targetRatio")
                    onComplete?.invoke()
                }
            })
            
            start()
        }
    }
    
    /**
     * 현재 비율 반환
     */
    fun getCurrentRatio(): Float = leftRatio
    
    /**
     * PIP에 앱 실행
     */
    fun launchApp(pipIndex: Int, packageName: String): Boolean {
        return when (pipIndex) {
            0 -> pip1Window?.launchApp(packageName) ?: false
            1 -> pip2Window?.launchApp(packageName) ?: false
            else -> false
        }
    }
    
    /**
     * PIP displayId 반환
     */
    fun getPipDisplayId(pipIndex: Int): Int {
        return when (pipIndex) {
            0 -> pip1Window?.displayId ?: -1
            1 -> pip2Window?.displayId ?: -1
            else -> -1
        }
    }
    
    /**
     * PIP 현재 패키지 반환
     */
    fun getPipCurrentPackage(pipIndex: Int): String? {
        return when (pipIndex) {
            0 -> pip1Window?.currentPackage
            1 -> pip2Window?.currentPackage
            else -> null
        }
    }
    
    /**
     * 표시/숨김
     */
    fun setVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        pip1Window?.visibility = visibility
        pip2Window?.visibility = visibility
        dividerWindow?.visibility = visibility
    }
    
    /**
     * 리소스 정리
     */
    fun release() {
        ratioAnimator?.cancel()
        
        pip1Window?.let {
            it.release()
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        pip2Window?.let {
            it.release()
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        dividerWindow?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        
        pip1Window = null
        pip2Window = null
        dividerWindow = null
        
        Log.d(TAG, "NativePipManager released")
    }
}

/**
 * NativePipWindow - 개별 PIP Window (SurfaceView 포함)
 */
@SuppressLint("ViewConstructor")
class NativePipWindow(
    context: Context,
    private val pipIndex: Int
) : SurfaceView(context), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "NativePipWindow"
        
        private var sSetDisplayIdMotion: java.lang.reflect.Method? = null
        private var sSetDisplayIdKey: java.lang.reflect.Method? = null
        private var sReflectionInitialized = false
        
        @SuppressLint("PrivateApi")
        private fun initReflection() {
            if (sReflectionInitialized) return
            
            try {
                sSetDisplayIdMotion = MotionEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdMotion?.isAccessible = true
            } catch (e: Exception) {
                Log.e(TAG, "MotionEvent.setDisplayId not found", e)
            }
            
            try {
                sSetDisplayIdKey = KeyEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdKey?.isAccessible = true
            } catch (e: Exception) {
                Log.e(TAG, "KeyEvent.setDisplayId not found", e)
            }
            
            sReflectionInitialized = true
        }
    }
    
    private val vdManager = VirtualDisplayManager.getInstance(context)
    
    var displayId = -1
        private set
    var currentPackage: String? = null
        private set
    
    private var displayDensity = 320
    private var touchDownTime = 0L
    
    init {
        initReflection()
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        
        // 별도 Window이므로 setZOrderOnTop 사용 가능
        setZOrderOnTop(true)
        
        setOnTouchListener { _, event -> handleTouchEvent(event) }
        
        Log.d(TAG, "NativePipWindow init: pipIndex=$pipIndex")
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated pipIndex=$pipIndex")
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: pipIndex=$pipIndex ${width}x${height}")
        
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            Log.e(TAG, "surfaceChanged: surface invalid")
            return
        }
        
        val slotIndex = 100 + pipIndex
        
        // 항상 getOrCreateDisplay 사용 - 내부에서 캐시와 Surface 재연결 처리
        val info = vdManager.getOrCreateDisplay(slotIndex, surface, width, height, displayDensity)
        if (info != null) {
            displayId = info.displayId
            Log.i(TAG, "VirtualDisplay ready: pipIndex=$pipIndex displayId=$displayId size=${width}x${height}")
        } else {
            Log.e(TAG, "Failed to create/update VirtualDisplay for pipIndex=$pipIndex")
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed pipIndex=$pipIndex")
        val slotIndex = 100 + pipIndex
        vdManager.detachSurface(slotIndex)
    }
    
    @SuppressLint("PrivateApi")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (displayId < 0) return false
        
        val action = event.actionMasked
        
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = SystemClock.uptimeMillis()
        }
        
        val injectedEvent = MotionEvent.obtain(event)
        
        try {
            sSetDisplayIdMotion?.invoke(injectedEvent, displayId)
            
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(injectedEvent, 0)
            
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                try {
                    WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Touch injection failed", e)
        } finally {
            injectedEvent.recycle()
        }
        
        return true
    }
    
    fun launchApp(packageName: String): Boolean {
        if (displayId < 0) {
            Log.e(TAG, "launchApp: displayId not ready")
            return false
        }
        
        if (currentPackage == packageName) {
            Log.i(TAG, "Same app already running: $packageName")
            return true
        }
        
        currentPackage = packageName
        
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.e(TAG, "launchApp: no launch intent for $packageName")
                return false
            }
            
            // ActivityOptions를 사용하여 특정 디스플레이에서 실행
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            
            // FLAG 설정
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            
            context.startActivity(launchIntent, options.toBundle())
            
            Log.i(TAG, "Launched $packageName on pipIndex=$pipIndex displayId=$displayId via ActivityOptions")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp exception: ${e.message}", e)
            
            // Fallback: am start via root
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            val component = launchIntent?.component?.flattenToShortString() ?: return false
            val escapedComponent = component.replace("$", "\\$")
            
            val cmd = "am start -n \"$escapedComponent\" --display $displayId --windowingMode 1 --activity-single-top --activity-clear-top --activity-reorder-to-front"
            
            Thread {
                val result = RootUtils.executeCommand(cmd)
                if (!result.success) {
                    Log.e(TAG, "launchApp fallback failed: ${result.error}")
                } else {
                    Log.d(TAG, "launchApp fallback success")
                }
            }.start()
            
            return true
        }
    }
    
    fun sendBackKey() {
        if (displayId < 0) return
        
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        
        try {
            sSetDisplayIdKey?.invoke(downEvent, displayId)
            sSetDisplayIdKey?.invoke(upEvent, displayId)
            
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(downEvent, 0)
            inputManager.injectInputEvent(upEvent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "sendBackKey failed", e)
        }
    }
    
    fun release() {
        val slotIndex = 100 + pipIndex
        vdManager.releaseDisplay(slotIndex)
        displayId = -1
        currentPackage = null
        Log.d(TAG, "NativePipWindow released: pipIndex=$pipIndex")
    }
}

/**
 * NativePipDivider - 구분선 Window (드래그 처리)
 */
@SuppressLint("ViewConstructor")
class NativePipDivider(context: Context) : View(context) {
    
    companion object {
        private const val TAG = "NativePipDivider"
    }
    
    private var containerWidth = 0
    private var minRatio = 0.25f
    private var maxRatio = 0.75f
    
    private var isDragging = false
    private var dragStartX = 0f
    private var longPressTriggered = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!longPressTriggered) {
            longPressTriggered = true
            isDragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            setBackgroundColor(Color.parseColor("#FFFF6B00"))
            Log.d(TAG, "Long press - drag enabled")
            onDragStart?.invoke(0f)
        }
    }
    
    // 콜백
    var onDragStart: ((Float) -> Unit)? = null
    var onDrag: ((Float) -> Unit)? = null  // deltaRatio
    var onDragEnd: (() -> Unit)? = null
    
    init {
        setBackgroundColor(Color.parseColor("#40FFFFFF"))
    }
    
    fun setContainerInfo(width: Int, minR: Float, maxR: Float) {
        containerWidth = width
        minRatio = minR
        maxRatio = maxR
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                longPressTriggered = false
                handler.postDelayed(longPressRunnable, 1000)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && containerWidth > 0) {
                    val deltaX = event.rawX - dragStartX
                    val deltaRatio = deltaX / containerWidth
                    onDrag?.invoke(deltaRatio)
                    dragStartX = event.rawX
                    return true
                } else if (handler.hasCallbacks(longPressRunnable)) {
                    val moveDistance = kotlin.math.abs(event.rawX - dragStartX)
                    if (moveDistance > 10) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                
                if (isDragging) {
                    isDragging = false
                    longPressTriggered = false
                    setBackgroundColor(Color.parseColor("#40FFFFFF"))
                    onDragEnd?.invoke()
                    Log.d(TAG, "Drag ended")
                    return true
                }
            }
        }
        
        return super.onTouchEvent(event)
    }
}
