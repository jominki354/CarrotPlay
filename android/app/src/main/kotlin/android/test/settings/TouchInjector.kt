package android.test.settings

import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.IWindowManager
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManagerGlobal

/**
 * TouchInjector - 원본 CarCarLauncher의 z7/q.java, z7/g.java 방식 구현
 * 
 * InputManager.injectInputEvent()를 사용하여 VirtualDisplay에 터치 이벤트를 직접 주입합니다.
 * 이 방식은 Root shell(input tap/swipe)보다 훨씬 빠르고 실시간 반응이 가능합니다.
 * 
 * 필수 조건:
 * - android:sharedUserId="android.uid.system"
 * - AOSP 플랫폼 키로 서명
 */
object TouchInjector {
    private const val TAG = "TouchInjector"
    
    private var inputManager: InputManager? = null
    private var windowManager: IWindowManager? = null
    private var isInitialized = false
    
    // 각 displayId별 downTime 추적 (원본 앱 방식)
    private val downTimeMap = mutableMapOf<Int, Long>()
    
    /**
     * 시스템 서비스 초기화
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        try {
            // InputManager.getInstance() - Hidden API
            inputManager = InputManager.getInstance()
            Log.d(TAG, "InputManager obtained: $inputManager")
            
            // IWindowManager via WindowManagerGlobal - Hidden API
            windowManager = WindowManagerGlobal.getWindowManagerService()
            Log.d(TAG, "IWindowManager obtained: $windowManager")
            
            isInitialized = inputManager != null && windowManager != null
            
            if (isInitialized) {
                Log.i(TAG, "TouchInjector initialized successfully (System API mode)")
            } else {
                Log.w(TAG, "TouchInjector initialization incomplete")
            }
            
            return isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TouchInjector", e)
            return false
        }
    }
    
    /**
     * 입력 트랜잭션 동기화 (원본 앱: syncInputTransactions(true))
     */
    private fun syncInput() {
        try {
            windowManager?.syncInputTransactions(true)
        } catch (e: Exception) {
            Log.w(TAG, "syncInputTransactions failed", e)
        }
    }
    
    /**
     * MotionEvent 주입 (내부 헬퍼)
     */
    private fun inject(event: MotionEvent): Boolean {
        return try {
            inputManager?.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
            true
        } catch (e: Exception) {
            Log.e(TAG, "injectInputEvent failed", e)
            false
        }
    }
    
    /**
     * KeyEvent 주입 (내부 헬퍼)
     */
    private fun injectKey(event: KeyEvent): Boolean {
        return try {
            inputManager?.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
            true
        } catch (e: Exception) {
            Log.e(TAG, "injectKeyEvent failed", e)
            false
        }
    }
    
    /**
     * 초기화 여부 확인
     */
    fun isAvailable(): Boolean = isInitialized
    
    /**
     * Flutter에서 호출하는 실시간 MotionEvent 주입 (원본 앱 방식)
     * Native에서 SystemClock.uptimeMillis() 기반으로 시간 관리
     * 
     * @param displayId VirtualDisplay의 displayId
     * @param action MotionEvent action (0=DOWN, 1=UP, 2=MOVE, 3=CANCEL)
     * @param x 터치 X 좌표
     * @param y 터치 Y 좌표
     * @param flutterDownTime Flutter의 downTime (사용 안함, Native에서 관리)
     * @param flutterEventTime Flutter의 eventTime (사용 안함, Native에서 관리)
     */
    fun injectMotionEventFromFlutter(
        displayId: Int,
        action: Int,
        x: Float,
        y: Float,
        flutterDownTime: Long,
        flutterEventTime: Long
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            val eventTime = SystemClock.uptimeMillis()
            
            // ACTION_DOWN일 때 downTime 기록
            val downTime: Long = when (action) {
                MotionEvent.ACTION_DOWN -> {
                    downTimeMap[displayId] = eventTime
                    eventTime
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dt = downTimeMap[displayId] ?: eventTime
                    downTimeMap.remove(displayId)
                    dt
                }
                else -> {
                    downTimeMap[displayId] ?: eventTime
                }
            }
            
            // MotionEvent 생성 (Native 시간 사용)
            val motionEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                x,
                y,
                0 // metaState
            )
            
            // displayId 설정
            setDisplayId(motionEvent, displayId)
            motionEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            
            val actionMasked = motionEvent.actionMasked
            
            // 원본 앱 로직 (z7/q.java): 
            // MOVE 전 sync는 성능에 영향 → UP 후에만 sync (최적화)
            val needsSyncAfter = actionMasked == MotionEvent.ACTION_UP ||
                                 actionMasked == MotionEvent.ACTION_POINTER_UP ||
                                 actionMasked == MotionEvent.ACTION_CANCEL ||
                                 actionMasked == MotionEvent.ACTION_HOVER_EXIT
            
            // 이벤트 주입
            inject(motionEvent)
            
            // 이벤트 후 동기화 (UP/CANCEL 시에만)
            if (needsSyncAfter) {
                syncInput()
            }
            
            motionEvent.recycle()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject MotionEvent from Flutter", e)
            return false
        }
    }
    
    /**
     * MotionEvent를 VirtualDisplay에 주입 (원본 앱 방식)
     * 
     * @param motionEvent Flutter에서 전달받은 MotionEvent
     * @param displayId VirtualDisplay의 displayId
     */
    fun injectMotionEvent(motionEvent: MotionEvent, displayId: Int): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            // displayId 설정 - Reflection 사용 (setDisplayId는 @hide)
            setDisplayId(motionEvent, displayId)
            
            val actionMasked = motionEvent.actionMasked
            
            // MOVE 이벤트 전에 동기화 (원본 앱 로직)
            val needsSyncBefore = actionMasked == MotionEvent.ACTION_MOVE || 
                                  actionMasked == MotionEvent.ACTION_HOVER_MOVE
            
            // UP 이벤트 후에 동기화 (원본 앱 로직)
            val needsSyncAfter = actionMasked == MotionEvent.ACTION_UP ||
                                 actionMasked == MotionEvent.ACTION_POINTER_UP ||
                                 actionMasked == MotionEvent.ACTION_CANCEL ||
                                 actionMasked == MotionEvent.ACTION_HOVER_EXIT
            
            // 이벤트 전 동기화
            if (needsSyncBefore) {
                syncInput()
            }
            
            // 이벤트 주입
            inject(motionEvent)
            
            // 이벤트 후 동기화
            if (needsSyncAfter) {
                syncInput()
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject MotionEvent", e)
            return false
        }
    }
    
    /**
     * setDisplayId - MotionEvent.setDisplayId()는 @hide이므로 리플렉션 사용
     */
    private fun setDisplayId(event: InputEvent, displayId: Int) {
        try {
            val method = InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            method.invoke(event, displayId)
        } catch (e: Exception) {
            Log.w(TAG, "setDisplayId reflection failed", e)
        }
    }
    
    /**
     * 단순 탭 이벤트 생성 및 주입
     */
    fun injectTap(displayId: Int, x: Float, y: Float): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            val downTime = SystemClock.uptimeMillis()
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                x, y, 0
            )
            setDisplayId(downEvent, displayId)
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            
            inject(downEvent)
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                x, y, 0
            )
            setDisplayId(upEvent, displayId)
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            
            syncInput()
            inject(upEvent)
            syncInput()
            
            downEvent.recycle()
            upEvent.recycle()
            
            Log.d(TAG, "Injected tap at ($x, $y) on display $displayId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject tap", e)
            return false
        }
    }
    
    /**
     * 스와이프 이벤트 생성 및 주입
     */
    fun injectSwipe(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            val downTime = SystemClock.uptimeMillis()
            val steps = 10
            val stepDuration = durationMs / steps
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                x1, y1, 0
            )
            setDisplayId(downEvent, displayId)
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            inject(downEvent)
            downEvent.recycle()
            
            // ACTION_MOVE (여러 단계)
            for (i in 1 until steps) {
                val progress = i.toFloat() / steps
                val currentX = x1 + (x2 - x1) * progress
                val currentY = y1 + (y2 - y1) * progress
                val eventTime = downTime + (stepDuration * i)
                
                val moveEvent = MotionEvent.obtain(
                    downTime, eventTime,
                    MotionEvent.ACTION_MOVE,
                    currentX, currentY, 0
                )
                setDisplayId(moveEvent, displayId)
                moveEvent.source = InputDevice.SOURCE_TOUCHSCREEN
                
                syncInput()
                inject(moveEvent)
                moveEvent.recycle()
                
                Thread.sleep(stepDuration)
            }
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                x2, y2, 0
            )
            setDisplayId(upEvent, displayId)
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            
            syncInput()
            inject(upEvent)
            syncInput()
            upEvent.recycle()
            
            Log.d(TAG, "Injected swipe from ($x1, $y1) to ($x2, $y2) on display $displayId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject swipe", e)
            return false
        }
    }
    
    /**
     * 롱프레스 이벤트
     */
    fun injectLongPress(displayId: Int, x: Float, y: Float, durationMs: Long = 800): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            val downTime = SystemClock.uptimeMillis()
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                x, y, 0
            )
            setDisplayId(downEvent, displayId)
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            inject(downEvent)
            downEvent.recycle()
            
            // 롱프레스 대기
            Thread.sleep(durationMs)
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                x, y, 0
            )
            setDisplayId(upEvent, displayId)
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            
            syncInput()
            inject(upEvent)
            syncInput()
            upEvent.recycle()
            
            Log.d(TAG, "Injected long press at ($x, $y) on display $displayId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject long press", e)
            return false
        }
    }
    
    /**
     * KeyEvent 주입 (Back, Home 등)
     */
    fun injectKeyEvent(displayId: Int, keyCode: Int): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        try {
            val now = SystemClock.uptimeMillis()
            
            // KEY_DOWN
            val downEvent = KeyEvent(
                now, now,
                KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, -1, 0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            setDisplayId(downEvent, displayId)
            injectKey(downEvent)
            
            // KEY_UP
            val upEvent = KeyEvent(
                now, now,
                KeyEvent.ACTION_UP,
                keyCode, 0, 0, -1, 0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            setDisplayId(upEvent, displayId)
            injectKey(upEvent)
            
            Log.d(TAG, "Injected key event $keyCode on display $displayId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject key event", e)
            return false
        }
    }
}
