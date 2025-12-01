package android.test.settings

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * VirtualDisplayViewFactory - Flutter PlatformView 팩토리
 * 
 * Flutter에서 AndroidView를 생성할 때 이 팩토리가 호출됩니다.
 * VirtualDisplayPlatformView 인스턴스를 생성하고 관리합니다.
 * 
 * 핵심 변경:
 * - touch 메서드 추가: PlatformViewTouch 데이터를 받아 터치 주입
 * - PlatformViewTouchHandler와 연동
 */
class VirtualDisplayViewFactory(
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    companion object {
        private const val TAG = "VDViewFactory"
        const val VIEW_TYPE = "android.test.settings/virtual_display_view"
        
        // 생성된 PlatformView 인스턴스 추적 (Flutter에서 제어하기 위해)
        private val viewInstances = mutableMapOf<Int, VirtualDisplayPlatformView>()
        
        fun getView(viewId: Int): VirtualDisplayPlatformView? = viewInstances[viewId]
        
        fun getAllViews(): Map<Int, VirtualDisplayPlatformView> = viewInstances.toMap()
    }

    private var methodChannel: MethodChannel? = null

    init {
        // MethodChannel 설정 (PlatformView 제어용)
        methodChannel = MethodChannel(messenger, "android.test.settings/virtual_display_view_channel").apply {
            setMethodCallHandler { call, result ->
                handleMethodCall(call, result)
            }
        }
        Log.d(TAG, "VirtualDisplayViewFactory initialized")
    }

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        Log.d(TAG, "Creating PlatformView viewId=$viewId args=$args")
        
        @Suppress("UNCHECKED_CAST")
        val creationParams = args as? Map<String, Any?>
        
        val platformView = VirtualDisplayPlatformView(context, viewId, creationParams)
        viewInstances[viewId] = platformView
        
        Log.i(TAG, "PlatformView created: viewId=$viewId, total=${viewInstances.size}")
        return platformView
    }

    /**
     * MethodChannel 핸들러 - Flutter에서 PlatformView 제어
     */
    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getDisplayId" -> {
                val viewId = call.argument<Int>("viewId") ?: -1
                val view = viewInstances[viewId]
                if (view != null) {
                    result.success(view.getDisplayId())
                } else {
                    result.error("VIEW_NOT_FOUND", "View $viewId not found", null)
                }
            }
            
            "launchApp" -> {
                val viewId = call.argument<Int>("viewId") ?: -1
                val packageName = call.argument<String>("packageName") ?: ""
                val view = viewInstances[viewId]
                
                if (view == null) {
                    result.error("VIEW_NOT_FOUND", "View $viewId not found", null)
                    return
                }
                
                if (packageName.isEmpty()) {
                    result.error("INVALID_PARAMS", "packageName is empty", null)
                    return
                }
                
                // displayId가 유효한지 확인
                val displayId = view.getDisplayId()
                if (displayId <= 0) {
                    Log.w(TAG, "launchApp: displayId not ready ($displayId), waiting...")
                    // displayId가 준비될 때까지 대기 (최대 2초)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val retryDisplayId = view.getDisplayId()
                        if (retryDisplayId > 0) {
                            val success = view.launchApp(packageName)
                            Log.d(TAG, "launchApp retry: $packageName on display $retryDisplayId -> $success")
                            result.success(success)
                        } else {
                            result.error("DISPLAY_NOT_READY", "displayId still not ready: $retryDisplayId", null)
                        }
                    }, 500)
                    return
                }
                
                val success = view.launchApp(packageName)
                Log.d(TAG, "launchApp: $packageName on display $displayId -> $success")
                result.success(success)
            }
            
            "resize" -> {
                val viewId = call.argument<Int>("viewId") ?: -1
                val width = call.argument<Int>("width") ?: 1080
                val height = call.argument<Int>("height") ?: 1920
                val dpi = call.argument<Int>("dpi") ?: 320
                val view = viewInstances[viewId]
                if (view != null) {
                    view.resize(width, height, dpi)
                    result.success(true)
                } else {
                    result.error("VIEW_NOT_FOUND", "View $viewId not found", null)
                }
            }
            
            "dispose" -> {
                val viewId = call.argument<Int>("viewId") ?: -1
                val view = viewInstances.remove(viewId)
                if (view != null) {
                    view.dispose()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }
            
            "getViewCount" -> {
                result.success(viewInstances.size)
            }
            
            // ============================================
            // PlatformViewTouch 터치 주입 메서드 (원본 앱 방식)
            // ============================================
            "touch" -> {
                val viewId = call.argument<Int>("viewId") ?: -1
                val view = viewInstances[viewId]
                
                if (view == null) {
                    result.error("VIEW_NOT_FOUND", "View $viewId not found", null)
                    return
                }
                
                try {
                    // PlatformViewTouch 데이터 파싱
                    val action = call.argument<Int>("action") ?: 0
                    val pointerCount = call.argument<Int>("pointerCount") ?: 1
                    val downTime = call.argument<Number>("downTime")?.toLong() ?: SystemClock.uptimeMillis()
                    val eventTime = call.argument<Number>("eventTime")?.toLong() ?: SystemClock.uptimeMillis()
                    val metaState = call.argument<Int>("metaState") ?: 0
                    val buttonState = call.argument<Int>("buttonState") ?: 0
                    val xPrecision = call.argument<Double>("xPrecision")?.toFloat() ?: 1.0f
                    val yPrecision = call.argument<Double>("yPrecision")?.toFloat() ?: 1.0f
                    val deviceId = call.argument<Int>("deviceId") ?: 0
                    val edgeFlags = call.argument<Int>("edgeFlags") ?: 0
                    val source = call.argument<Int>("source") ?: InputDevice.SOURCE_TOUCHSCREEN
                    val flags = call.argument<Int>("flags") ?: 0
                    
                    // rawPointerCoords / rawPointerPropertiesList 파싱
                    @Suppress("UNCHECKED_CAST")
                    val rawCoordsList = call.argument<List<List<Double>>>("rawPointerCoords")
                    @Suppress("UNCHECKED_CAST") 
                    val rawPropsList = call.argument<List<List<Int>>>("rawPointerPropertiesList")
                    
                    val pointerCoords = Array(pointerCount) { MotionEvent.PointerCoords() }
                    val pointerProps = Array(pointerCount) { MotionEvent.PointerProperties() }
                    
                    val density = view.appViewSurface.context.resources.displayMetrics.density
                    val touchOffsetX = view.getTouchOffsetX()
                    val touchOffsetY = view.getTouchOffsetY()
                    
                    for (i in 0 until pointerCount) {
                        // PointerCoords 파싱 (원본 o1/b.java와 동일한 순서)
                        val coords = rawCoordsList?.getOrNull(i)
                        if (coords != null && coords.size >= 9) {
                            pointerCoords[i].orientation = coords[0].toFloat()
                            pointerCoords[i].pressure = coords[1].toFloat().let { if (it == 0f) 1f else it }
                            pointerCoords[i].size = coords[2].toFloat().let { if (it == 0f) 1f else it }
                            pointerCoords[i].toolMajor = coords[3].toFloat() * density
                            pointerCoords[i].toolMinor = coords[4].toFloat() * density
                            pointerCoords[i].touchMajor = coords[5].toFloat() * density
                            pointerCoords[i].touchMinor = coords[6].toFloat() * density
                            pointerCoords[i].x = touchOffsetX + coords[7].toFloat()
                            pointerCoords[i].y = touchOffsetY + coords[8].toFloat()
                        }
                        
                        // PointerProperties 파싱
                        val props = rawPropsList?.getOrNull(i)
                        if (props != null && props.size >= 2) {
                            pointerProps[i].id = props[0]
                            pointerProps[i].toolType = props[1]
                        } else {
                            pointerProps[i].id = i
                            pointerProps[i].toolType = MotionEvent.TOOL_TYPE_FINGER
                        }
                    }
                    
                    // MotionEvent 생성
                    val motionEvent = MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        pointerCount,
                        pointerProps,
                        pointerCoords,
                        metaState,
                        buttonState,
                        xPrecision,
                        yPrecision,
                        deviceId,
                        edgeFlags,
                        source,
                        flags
                    )
                    
                    // 터치 주입
                    view.dispatchTouchEvent(motionEvent)
                    motionEvent.recycle()
                    
                    result.success(true)
                } catch (e: Exception) {
                    Log.e(TAG, "touch method failed", e)
                    result.error("TOUCH_ERROR", e.message, null)
                }
            }
            
            else -> {
                result.notImplemented()
            }
        }
    }

    /**
     * 팩토리 정리 (앱 종료 시 호출)
     */
    fun dispose() {
        Log.d(TAG, "Disposing VirtualDisplayViewFactory")
        viewInstances.values.forEach { it.dispose() }
        viewInstances.clear()
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }
}
