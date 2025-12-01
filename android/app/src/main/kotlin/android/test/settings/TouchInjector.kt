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
    
    // 터치 주입용 deviceId
    // 0 = 가상 터치 장치 (시스템이 인식)
    // 실제 터치스크린 ID를 사용하면 물리 터치와 충돌 가능
    private var touchScreenDeviceId: Int = 0
    
    /**
     * 시스템의 터치스크린 장치 ID를 찾습니다
     */
    private fun findTouchScreenDeviceId(): Int {
        try {
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val device = InputDevice.getDevice(id)
                if (device != null && 
                    (device.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) {
                    Log.d(TAG, "Found touchscreen device: id=$id name=${device.name}")
                    // 찾은 ID 반환 (실제 사용은 하지 않음)
                    return id
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find touchscreen device", e)
        }
        return 0
    }
    
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
            
            // 터치스크린 장치 ID 찾기
            touchScreenDeviceId = findTouchScreenDeviceId()
            Log.d(TAG, "Using touchscreen deviceId: $touchScreenDeviceId")
            
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
     * VirtualDisplay 크기 변경 후 입력 동기화 (public)
     * 원본 앱: resize 후 반드시 호출
     */
    fun syncAfterResize() {
        Log.d(TAG, "syncAfterResize called")
        syncInput()
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
     * @param device Flutter PointerEvent의 device id (참고용 - 현재 터치스크린 deviceId 사용)
     * @param pressure Flutter PointerEvent의 pressure
     * @param size Flutter PointerEvent의 size
     * @param source Flutter PointerEvent의 source 타입 (기본: TOUCHSCREEN)
     * @param toolType Flutter PointerEvent의 tool type (기본: FINGER)
     * @param pointerId Flutter PointerEvent의 pointer id
     */
    fun injectMotionEventFromFlutter(
        displayId: Int,
        action: Int,
        x: Float,
        y: Float,
        flutterDownTime: Long,
        flutterEventTime: Long,
        device: Int = 0,
        pressure: Float = 1.0f,
        size: Float = 1.0f,
        source: Int = InputDevice.SOURCE_TOUCHSCREEN,
        toolType: Int = MotionEvent.TOOL_TYPE_FINGER,
        pointerId: Int = 0
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TouchInjector not initialized")
            return false
        }
        
        val actionName = when(action) {
            0 -> "DOWN"
            1 -> "UP"
            2 -> "MOVE"
            3 -> "CANCEL"
            else -> "UNKNOWN($action)"
        }
        Log.d(TAG, "[NATIVE] injectMotionEvent action=$actionName displayId=$displayId pos=(${x.toInt()}, ${y.toInt()}) pressure=$pressure size=$size")
        
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
            
            // PointerCoords 및 PointerProperties 설정 (Flutter에서 전달받은 값 사용)
            val pointerProperties = MotionEvent.PointerProperties().apply {
                id = pointerId
                this.toolType = toolType
            }
            val pointerCoords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                this.pressure = pressure
                this.size = size
            }
            
            // MotionEvent 생성 (실제 터치스크린 deviceId 사용)
            // 원본 앱도 deviceId는 터치스크린의 실제 ID를 사용
            val motionEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1, // pointerCount
                arrayOf(pointerProperties),
                arrayOf(pointerCoords),
                0, // metaState
                0, // buttonState
                1.0f, // xPrecision
                1.0f, // yPrecision
                touchScreenDeviceId, // 실제 터치스크린 장치 ID 사용
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN, // 항상 TOUCHSCREEN 사용 (원본 앱 방식)
                0 // flags
            )
            
            // displayId 설정
            setDisplayId(motionEvent, displayId)
            
            val actionMasked = motionEvent.actionMasked
            
            // 원본 앱 방식 sync 로직 (z7/g.java의 b 메서드 그대로)
            // boolean z5 = !motionEvent.isFromSource(8194) 
            //     ? !(actionMasked == 0 || actionMasked == 5 || actionMasked == 9) 
            //     : actionMasked == 7 || actionMasked == 2;
            // 
            // 터치스크린(SOURCE_TOUCHSCREEN)은 SOURCE_MOUSE(8194)가 아니므로:
            // - DOWN(0), POINTER_DOWN(5), HOVER_ENTER(9)가 아닌 모든 이벤트 전에 sync
            // - 즉 MOVE, UP, POINTER_UP, CANCEL, HOVER_MOVE 등 모든 이벤트 전에 sync
            val needsSyncBefore = !(actionMasked == MotionEvent.ACTION_DOWN ||
                                    actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
                                    actionMasked == MotionEvent.ACTION_HOVER_ENTER)
            
            // UP/POINTER_UP/CANCEL/HOVER_EXIT 후에 sync
            val needsSyncAfter = actionMasked == MotionEvent.ACTION_UP ||
                                 actionMasked == MotionEvent.ACTION_POINTER_UP ||
                                 actionMasked == MotionEvent.ACTION_CANCEL ||
                                 actionMasked == MotionEvent.ACTION_HOVER_EXIT
            
            // 이벤트 전 동기화 (MOVE 전)
            if (needsSyncBefore) {
                syncInput()
            }
            
            // 이벤트 주입
            inject(motionEvent)
            
            // 이벤트 후 동기화 (UP/CANCEL 후)
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
        
        Log.d(TAG, "[NATIVE] injectTap START displayId=$displayId pos=(${x.toInt()}, ${y.toInt()})")
        
        try {
            val downTime = SystemClock.uptimeMillis()
            
            // PointerCoords 및 PointerProperties 설정
            val pointerProperties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val pointerCoords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1.0f
                size = 1.0f
            }
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(downEvent, displayId)
            
            val downResult = inject(downEvent)
            Log.d(TAG, "[NATIVE] injectTap DOWN result=$downResult")
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            pointerCoords.x = x
            pointerCoords.y = y
            
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(upEvent, displayId)
            
            syncInput()
            val upResult = inject(upEvent)
            syncInput()
            
            Log.d(TAG, "[NATIVE] injectTap UP result=$upResult, elapsed=${upTime - downTime}ms")
            
            downEvent.recycle()
            upEvent.recycle()
            
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
            
            // PointerCoords 및 PointerProperties 설정
            val pointerProperties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val pointerCoords = MotionEvent.PointerCoords().apply {
                x = x1
                y = y1
                pressure = 1.0f
                size = 1.0f
            }
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(downEvent, displayId)
            inject(downEvent)
            downEvent.recycle()
            
            // ACTION_MOVE (여러 단계)
            for (i in 1 until steps) {
                val progress = i.toFloat() / steps
                val currentX = x1 + (x2 - x1) * progress
                val currentY = y1 + (y2 - y1) * progress
                val eventTime = downTime + (stepDuration * i)
                
                pointerCoords.x = currentX
                pointerCoords.y = currentY
                
                val moveEvent = MotionEvent.obtain(
                    downTime, eventTime,
                    MotionEvent.ACTION_MOVE,
                    1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                    0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                    InputDevice.SOURCE_TOUCHSCREEN, 0
                )
                setDisplayId(moveEvent, displayId)
                
                syncInput()
                inject(moveEvent)
                moveEvent.recycle()
                
                Thread.sleep(stepDuration)
            }
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            pointerCoords.x = x2
            pointerCoords.y = y2
            
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(upEvent, displayId)
            
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
            
            // PointerCoords 및 PointerProperties 설정
            val pointerProperties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val pointerCoords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1.0f
                size = 1.0f
            }
            
            // ACTION_DOWN
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(downEvent, displayId)
            inject(downEvent)
            downEvent.recycle()
            
            // 롱프레스 대기
            Thread.sleep(durationMs)
            
            // ACTION_UP
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                1, arrayOf(pointerProperties), arrayOf(pointerCoords),
                0, 0, 1.0f, 1.0f, touchScreenDeviceId, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            setDisplayId(upEvent, displayId)
            
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
