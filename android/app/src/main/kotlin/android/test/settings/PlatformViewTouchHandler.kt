package android.test.settings

import android.hardware.input.InputManager
import android.util.Log
import android.view.IWindowManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManagerGlobal
import io.flutter.embedding.engine.systemchannels.PlatformViewTouch
import java.lang.reflect.Method

/**
 * PlatformViewTouchHandler - 원본 CarCarLauncher o1/b.java의 d(PlatformViewTouch) 구현
 * 
 * Flutter의 PlatformViewTouch 메시지를 처리하여 VirtualDisplay에 터치 주입.
 * 핵심: motionEventId를 통해 AndroidTouchProcessor의 MotionEventTracker에서
 * 원본 MotionEvent를 검색하여 deviceId, downTime, source 등을 유지.
 * 
 * 원본 코드 흐름:
 * 1. Flutter PlatformViewsChannel -> PlatformViewTouch 전송
 * 2. motionEventId로 MotionEventTracker.pop() 호출 -> 원본 MotionEvent 검색
 * 3. 원본 MotionEvent의 속성(deviceId, downTime, source 등) 유지
 * 4. 좌표만 rawPointerCoords에서 가져와서 적용
 * 5. displayId 설정 후 injectInputEvent()
 */
object PlatformViewTouchHandler {
    private const val TAG = "PlatformViewTouchHandler"
    
    private var inputManager: InputManager? = null
    private var windowManager: IWindowManager? = null
    private var motionEventTracker: Any? = null
    private var popMethod: Method? = null
    private var isInitialized = false
    
    // PlatformView ID -> AppViewSurface 매핑
    private val viewMap = mutableMapOf<Int, AppViewSurface>()
    
    /**
     * 초기화 - InputManager, IWindowManager, MotionEventTracker 설정
     */
    fun initialize(tracker: Any?): Boolean {
        if (isInitialized) return true
        
        try {
            // InputManager.getInstance()
            inputManager = InputManager.getInstance()
            Log.d(TAG, "InputManager obtained: $inputManager")
            
            // IWindowManager via WindowManagerGlobal
            windowManager = WindowManagerGlobal.getWindowManagerService()
            Log.d(TAG, "IWindowManager obtained: $windowManager")
            
            // MotionEventTracker 설정
            if (tracker != null) {
                motionEventTracker = tracker
                
                // pop(MotionEventId) 메서드 찾기
                // 원본: MotionEventTracker.H0(new z(platformViewTouch.motionEventId))
                // Flutter 내부: pop(MotionEventId) 메서드
                val trackerClass = tracker.javaClass
                for (method in trackerClass.declaredMethods) {
                    Log.d(TAG, "MotionEventTracker method: ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                    if (method.returnType == MotionEvent::class.java && method.parameterCount == 1) {
                        popMethod = method
                        popMethod?.isAccessible = true
                        Log.d(TAG, "Found pop method: ${method.name}")
                        break
                    }
                }
                
                if (popMethod == null) {
                    Log.w(TAG, "pop method not found in MotionEventTracker")
                }
            } else {
                Log.w(TAG, "MotionEventTracker is null")
            }
            
            isInitialized = inputManager != null && windowManager != null
            Log.i(TAG, "PlatformViewTouchHandler initialized: $isInitialized")
            
            return isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PlatformViewTouchHandler", e)
            return false
        }
    }
    
    /**
     * PlatformView 등록
     */
    fun registerView(viewId: Int, surface: AppViewSurface) {
        viewMap[viewId] = surface
        Log.d(TAG, "Registered view: viewId=$viewId displayId=${surface.displayId}")
    }
    
    /**
     * PlatformView 해제
     */
    fun unregisterView(viewId: Int) {
        viewMap.remove(viewId)
        Log.d(TAG, "Unregistered view: viewId=$viewId")
    }
    
    /**
     * PlatformViewTouch 처리 - 원본 o1/b.java의 d(PlatformViewTouch) 구현
     * 
     * @param viewId PlatformView ID
     * @param touch PlatformViewTouch 데이터
     */
    fun handleTouch(viewId: Int, touch: PlatformViewTouch) {
        val surface = viewMap[viewId]
        if (surface == null) {
            Log.w(TAG, "View not found: viewId=$viewId")
            return
        }
        
        val displayId = surface.displayId
        if (displayId < 0) {
            Log.w(TAG, "Invalid displayId: $displayId")
            return
        }
        
        try {
            // 1. rawPointerCoords에서 좌표 추출 (원본 o1/b.java와 동일)
            val rawCoordsList = touch.rawPointerCoords as? List<*>
            val rawPropsList = touch.rawPointerPropertiesList as? List<*>
            
            if (rawCoordsList == null || rawPropsList == null) {
                Log.w(TAG, "Invalid pointer data")
                return
            }
            
            val pointerCount = touch.pointerCount
            val pointerCoords = Array(pointerCount) { MotionEvent.PointerCoords() }
            val pointerProps = Array(pointerCount) { MotionEvent.PointerProperties() }
            
            // density 스케일 팩터
            val density = surface.context.resources.displayMetrics.density
            
            for (i in 0 until pointerCount) {
                // PointerCoords 파싱 (원본 o1/b.java와 동일한 순서)
                val coordsList = rawCoordsList[i] as? List<*>
                if (coordsList != null && coordsList.size >= 9) {
                    pointerCoords[i].orientation = (coordsList[0] as? Double)?.toFloat() ?: 0f
                    pointerCoords[i].pressure = (coordsList[1] as? Double)?.toFloat() ?: 1f
                    pointerCoords[i].size = (coordsList[2] as? Double)?.toFloat() ?: 1f
                    pointerCoords[i].toolMajor = ((coordsList[3] as? Double)?.toFloat() ?: 0f) * density
                    pointerCoords[i].toolMinor = ((coordsList[4] as? Double)?.toFloat() ?: 0f) * density
                    pointerCoords[i].touchMajor = ((coordsList[5] as? Double)?.toFloat() ?: 0f) * density
                    pointerCoords[i].touchMinor = ((coordsList[6] as? Double)?.toFloat() ?: 0f) * density
                    pointerCoords[i].x = surface.touchOffsetX + ((coordsList[7] as? Double)?.toFloat() ?: 0f)
                    pointerCoords[i].y = surface.touchOffsetY + ((coordsList[8] as? Double)?.toFloat() ?: 0f)
                    
                    // pressure/size 0일 때 기본값 (원본 코드와 동일)
                    if (pointerCoords[i].size == 0f) pointerCoords[i].size = 1f
                    if (pointerCoords[i].pressure == 0f) pointerCoords[i].pressure = 1f
                }
                
                // PointerProperties 파싱
                val propsList = rawPropsList[i] as? List<*>
                if (propsList != null && propsList.size >= 2) {
                    pointerProps[i].id = (propsList[0] as? Int) ?: i
                    pointerProps[i].toolType = (propsList[1] as? Int) ?: MotionEvent.TOOL_TYPE_FINGER
                }
            }
            
            // 2. motionEventId로 원본 MotionEvent 검색 (핵심!)
            val motionEventId = touch.motionEventId
            var originalEvent: MotionEvent? = null
            
            if (motionEventTracker != null && popMethod != null && motionEventId != 0L) {
                try {
                    // MotionEventId 객체 생성 (Flutter 내부 클래스)
                    val motionEventIdClass = Class.forName("io.flutter.embedding.android.MotionEventTracker\$MotionEventId")
                    val fromMethod = motionEventIdClass.getDeclaredMethod("from", Long::class.javaPrimitiveType)
                    fromMethod.isAccessible = true
                    val eventIdObj = fromMethod.invoke(null, motionEventId)
                    
                    // pop() 호출로 원본 MotionEvent 검색
                    originalEvent = popMethod?.invoke(motionEventTracker, eventIdObj) as? MotionEvent
                    
                    if (originalEvent != null) {
                        Log.d(TAG, "Found original MotionEvent: deviceId=${originalEvent.deviceId} source=${originalEvent.source}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get original MotionEvent", e)
                }
            }
            
            // 3. MotionEvent 생성 (원본 있으면 속성 사용, 없으면 PlatformViewTouch 속성 사용)
            val motionEvent: MotionEvent
            if (originalEvent != null) {
                // 원본 MotionEvent의 속성(deviceId, downTime, source 등) 사용 (o1/b.java 방식)
                motionEvent = MotionEvent.obtain(
                    originalEvent.downTime,
                    originalEvent.eventTime,
                    touch.action,
                    pointerCount,
                    pointerProps,
                    pointerCoords,
                    originalEvent.metaState,
                    originalEvent.buttonState,
                    originalEvent.xPrecision,
                    originalEvent.yPrecision,
                    originalEvent.deviceId,
                    originalEvent.edgeFlags,
                    originalEvent.source,
                    originalEvent.flags
                )
            } else {
                // Fallback: PlatformViewTouch의 속성 사용
                motionEvent = MotionEvent.obtain(
                    touch.downTime.toLong(),
                    touch.eventTime.toLong(),
                    touch.action,
                    pointerCount,
                    pointerProps,
                    pointerCoords,
                    touch.metaState,
                    touch.buttonState,
                    touch.xPrecision,
                    touch.yPrecision,
                    touch.deviceId,
                    touch.edgeFlags,
                    touch.source,
                    touch.flags
                )
            }
            
            // 4. displayId 설정 후 주입 (z7/g.java의 b(MotionEvent)와 동일)
            injectToDisplay(motionEvent, displayId)
            
            motionEvent.recycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle touch", e)
        }
    }
    
    /**
     * 터치 이벤트 직접 처리 (PlatformViewTouch 없이)
     * VirtualDisplayPlatformView에서 직접 호출
     */
    fun handleRawTouch(viewId: Int, event: MotionEvent) {
        val surface = viewMap[viewId]
        if (surface == null) {
            Log.w(TAG, "View not found for raw touch: viewId=$viewId")
            return
        }
        
        val displayId = surface.displayId
        if (displayId < 0) {
            Log.w(TAG, "Invalid displayId for raw touch: $displayId")
            return
        }
        
        injectToDisplay(event, displayId)
    }
    
    /**
     * MotionEvent를 VirtualDisplay에 주입
     * 
     * 성능 최적화 v2:
     * - MOVE에서 syncInputTransactions 완전 제거 (가장 큰 병목)
     * - UP/CANCEL 시에만 sync (터치 완료 정확성 유지)
     * - 리플렉션 Method 캐싱
     * - 로그 완전 제거
     */
    // 리플렉션 Method 캐싱 (한 번만 초기화)
    private val setDisplayIdMethod: java.lang.reflect.Method? by lazy {
        try {
            MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType).also {
                it.isAccessible = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "setDisplayId method not found", e)
            null
        }
    }
    
    private fun injectToDisplay(motionEvent: MotionEvent, displayId: Int) {
        try {
            // displayId 설정 (캐싱된 Method 사용)
            setDisplayIdMethod?.invoke(motionEvent, displayId)
            
            val actionMasked = motionEvent.actionMasked
            
            // 성능 최적화 v2: MOVE에서 sync 완전 제거!
            // UP/CANCEL 시에만 sync (터치 완료 정확성 유지)
            val syncAfter = actionMasked == MotionEvent.ACTION_UP ||
                           actionMasked == MotionEvent.ACTION_POINTER_UP ||
                           actionMasked == MotionEvent.ACTION_CANCEL
            
            // 이벤트 주입 (완전 비동기)
            inputManager?.injectInputEvent(motionEvent, 0)
            
            // UP/CANCEL 후에만 sync
            if (syncAfter) {
                windowManager?.syncInputTransactions(true)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject to display", e)
        }
    }
    
    /**
     * 초기화 여부
     */
    fun isAvailable(): Boolean = isInitialized
}
