package android.test.settings.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManagerGlobal
import java.lang.reflect.Method

/**
 * AppViewSurface - VirtualDisplay 기반 앱 표시 및 터치 주입 (Kotlin)
 * 
 * Hidden API Stub JAR (compileOnly)를 통해 직접 호출
 * - InputManager.getInstance().injectInputEvent()
 * - WindowManagerGlobal.getWindowManagerService().syncInputTransactions()
 */
class AppViewSurface : SurfaceView, SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "AppViewSurface"
        
        // Reflection 캐시 (setDisplayId는 Hidden API)
        private var sSetDisplayIdMotion: Method? = null
        private var sSetDisplayIdKey: Method? = null
        private var sReflectionInitialized = false
        
        @Synchronized
        @SuppressLint("PrivateApi")
        private fun initReflection() {
            if (sReflectionInitialized) return
            
            try {
                sSetDisplayIdMotion = MotionEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdMotion?.isAccessible = true
                Log.d(TAG, "Reflection: MotionEvent.setDisplayId initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Reflection: MotionEvent.setDisplayId not found", e)
            }
            
            try {
                sSetDisplayIdKey = KeyEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdKey?.isAccessible = true
                Log.d(TAG, "Reflection: KeyEvent.setDisplayId initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Reflection: KeyEvent.setDisplayId not found", e)
            }
            
            sReflectionInitialized = true
        }
    }
    
    // 터치 오프셋
    var touchOffsetX = 0f
    var touchOffsetY = 0f
    
    // VirtualDisplayManager
    private lateinit var vdManager: VirtualDisplayManager
    private var slotIndex = -1
    
    private var displayId = -1
    private var targetPackage: String? = null
    private var surfaceReady = false
    private var displayCreated = false
    private var displayWidth = 800
    private var displayHeight = 480
    private var displayDensity = 160
    private var isTouchEnabled = true
    
    private lateinit var mainHandler: Handler
    
    // 콜백
    interface OnDisplayReadyListener {
        fun onDisplayReady(displayId: Int)
    }
    
    private var displayReadyListener: OnDisplayReadyListener? = null
    
    constructor(context: Context) : super(context) {
        init(context)
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }
    
    constructor(context: Context, slotIndex: Int, width: Int, height: Int, density: Int) : super(context) {
        this.slotIndex = slotIndex
        this.displayWidth = width
        this.displayHeight = height
        this.displayDensity = density
        init(context)
    }
    
    private fun init(context: Context) {
        initReflection()
        vdManager = VirtualDisplayManager.getInstance(context)
        mainHandler = Handler(Looper.getMainLooper())
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        Log.d(TAG, "AppViewSurface init: slotIndex=$slotIndex")
    }
    
    private fun setMotionEventDisplayId(event: MotionEvent, displayId: Int) {
        sSetDisplayIdMotion?.let { method ->
            try {
                method.invoke(event, displayId)
            } catch (e: Exception) {
                Log.e(TAG, "MotionEvent.setDisplayId failed", e)
            }
        }
    }
    
    private fun setKeyEventDisplayId(event: KeyEvent, displayId: Int) {
        sSetDisplayIdKey?.let { method ->
            try {
                method.invoke(event, displayId)
            } catch (e: Exception) {
                Log.e(TAG, "KeyEvent.setDisplayId failed", e)
            }
        }
    }
    
    fun setSlotIndex(index: Int) {
        slotIndex = index
        Log.d(TAG, "slotIndex set to: $index")
    }
    
    fun getSlotIndex() = slotIndex
    
    fun setTargetPackage(packageName: String) {
        targetPackage = packageName
    }
    
    fun setDisplaySize(width: Int, height: Int, density: Int) {
        displayWidth = width
        displayHeight = height
        displayDensity = density
        
        if (slotIndex >= 0) {
            vdManager.getDisplayInfo(slotIndex)?.let { info ->
                info.virtualDisplay.resize(width, height, density)
                info.width = width
                info.height = height
                info.density = density
            }
        }
    }
    
    fun setOnDisplayReadyListener(listener: OnDisplayReadyListener?) {
        displayReadyListener = listener
    }
    
    fun getDisplayId() = displayId
    fun getDisplayWidth() = displayWidth
    fun getDisplayHeight() = displayHeight
    fun getDisplayDensity() = displayDensity
    fun isTouchEnabled() = isTouchEnabled
    fun setTouchEnabled(enabled: Boolean) { isTouchEnabled = enabled }
    fun isDisplayCreated() = displayCreated
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated slotIndex=$slotIndex")
        surfaceReady = true
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: slotIndex=$slotIndex ${width}x$height")
        
        if (slotIndex < 0) {
            Log.e(TAG, "surfaceChanged: slotIndex not set!")
            return
        }
        
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            Log.e(TAG, "surfaceChanged: surface is invalid")
            return
        }
        
        // Surface의 실제 크기 사용
        displayWidth = width
        displayHeight = height
        
        // 밀도 계산 (화면 밀도 기반)
        val metrics = context.resources.displayMetrics
        displayDensity = metrics.densityDpi
        
        // VirtualDisplayManager를 통해 캐시된 VirtualDisplay 사용 또는 새로 생성
        val info = vdManager.getOrCreateDisplay(slotIndex, surface, displayWidth, displayHeight, displayDensity)
        
        if (info != null) {
            displayId = info.displayId
            displayCreated = true
            displayWidth = info.width
            displayHeight = info.height
            Log.i(TAG, "VirtualDisplay ready: slotIndex=$slotIndex displayId=$displayId size=${displayWidth}x${displayHeight} dpi=$displayDensity")
            
            // 콜백 호출
            mainHandler.post { displayReadyListener?.onDisplayReady(displayId) }
        } else {
            Log.e(TAG, "Failed to get/create VirtualDisplay")
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed slotIndex=$slotIndex")
        surfaceReady = false
        
        if (slotIndex >= 0) {
            vdManager.detachSurface(slotIndex)
        }
    }
    
    fun releaseVirtualDisplay() {
        if (slotIndex >= 0) {
            vdManager.releaseDisplay(slotIndex)
        }
        displayId = -1
        displayCreated = false
        Log.d(TAG, "VirtualDisplay released: slotIndex=$slotIndex")
    }
    
    /**
     * 앱 실행 on VirtualDisplay
     */
    fun launchApp(packageName: String): Boolean {
        if (!displayCreated || displayId < 0) {
            Log.e(TAG, "Cannot launch app: display not ready")
            return false
        }
        
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Log.e(TAG, "Cannot find launch intent for: $packageName")
            return false
        }
        
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        
        return try {
            val options = android.app.ActivityOptions.makeBasic()
            options.setLaunchDisplayId(displayId)
            context.startActivity(launchIntent, options.toBundle())
            targetPackage = packageName
            Log.d(TAG, "Launched $packageName on display $displayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
            false
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!displayCreated || displayId < 0 || !isTouchEnabled) {
            return super.onTouchEvent(event)
        }
        injectTouchEvent(event)
        return true
    }
    
    /**
     * 외부에서 호출 가능한 터치 주입 메서드
     */
    @SuppressLint("PrivateApi")
    fun dispatchTouchToVirtualDisplay(motionEvent: MotionEvent) {
        if (!displayCreated || displayId < 0) return
        
        setMotionEventDisplayId(motionEvent, displayId)
        
        val actionMasked = motionEvent.actionMasked
        val syncAfter = actionMasked == MotionEvent.ACTION_UP ||
                        actionMasked == MotionEvent.ACTION_POINTER_UP ||
                        actionMasked == MotionEvent.ACTION_CANCEL
        
        try {
            // Hidden API Stub를 통한 직접 호출
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(motionEvent, 0)
            
            if (syncAfter) {
                WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "dispatchTouchToVirtualDisplay failed", e)
        }
    }
    
    @SuppressLint("PrivateApi")
    private fun injectTouchEvent(event: MotionEvent) {
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return
        
        val scaleX = displayWidth.toFloat() / viewWidth
        val scaleY = displayHeight.toFloat() / viewHeight
        
        val scaledEvent = MotionEvent.obtain(event)
        scaledEvent.setLocation(event.x * scaleX, event.y * scaleY)
        setMotionEventDisplayId(scaledEvent, displayId)
        
        val actionMasked = scaledEvent.actionMasked
        val syncAfter = actionMasked == MotionEvent.ACTION_UP ||
                        actionMasked == MotionEvent.ACTION_POINTER_UP ||
                        actionMasked == MotionEvent.ACTION_CANCEL
        
        try {
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(scaledEvent, 0)
            
            if (syncAfter) {
                WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true)
            }
        } catch (e: Exception) {
            // Silent
        } finally {
            scaledEvent.recycle()
        }
    }
    
    fun sendBackKey() {
        if (!displayCreated || displayId < 0) return
        
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        setKeyEventDisplayId(downEvent, displayId)
        setKeyEventDisplayId(upEvent, displayId)
        
        try {
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(downEvent, 0)
            inputManager.injectInputEvent(upEvent, 0)
            Log.d(TAG, "Back key sent to display $displayId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send back key", e)
        }
    }
    
    fun sendHomeKey() {
        if (!displayCreated || displayId < 0) return
        
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 0)
        setKeyEventDisplayId(downEvent, displayId)
        setKeyEventDisplayId(upEvent, displayId)
        
        try {
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(downEvent, 0)
            inputManager.injectInputEvent(upEvent, 0)
            Log.d(TAG, "Home key sent to display $displayId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send home key", e)
        }
    }
    
    fun release() {
        Log.d(TAG, "release() called")
        if (slotIndex >= 0) {
            vdManager.detachSurface(slotIndex)
        }
    }
    
    fun dispose() {
        Log.d(TAG, "dispose() called")
        if (slotIndex >= 0) {
            vdManager.detachSurface(slotIndex)
        }
    }
    
    fun fullRelease() {
        Log.d(TAG, "fullRelease() called")
        releaseVirtualDisplay()
    }
}
