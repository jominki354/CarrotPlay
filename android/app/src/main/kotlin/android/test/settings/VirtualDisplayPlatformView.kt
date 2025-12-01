package android.test.settings

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import io.flutter.plugin.platform.PlatformView

/**
 * VirtualDisplayPlatformView - AppViewSurface를 래핑하는 PlatformView
 * 
 * AppViewSurface (Java, Hidden API 직접 호출)를 Flutter PlatformView로 노출합니다.
 * 원본 CarCarLauncher와 동일한 방식으로 동작합니다.
 * 
 * 핵심 변경:
 * - slotIndex를 AppViewSurface에 전달하여 VirtualDisplayManager 캐시 키로 사용
 * - PlatformView가 재생성되어도 같은 slotIndex의 VirtualDisplay 재사용
 * - dispose()에서 VirtualDisplay를 release하지 않고 캐시에 유지
 * - dispatchTouchEvent(): 외부에서 터치 이벤트를 직접 주입할 수 있도록 노출
 */
class VirtualDisplayPlatformView(
    private val context: Context,
    private val viewId: Int,
    private val creationParams: Map<String, Any?>?
) : PlatformView {

    companion object {
        private const val TAG = "VDPlatformView"
    }

    val appViewSurface: AppViewSurface
    
    // VirtualDisplay 크기
    private var displayWidth: Int = 1080
    private var displayHeight: Int = 1920
    private var displayDpi: Int = 320
    
    // 슬롯 인덱스 (VirtualDisplayManager 캐시 키)
    // viewId는 Flutter에서 할당되어 변경될 수 있지만, slotIndex는 PIP 슬롯을 고유하게 식별
    private val slotIndex: Int

    init {
        Log.d(TAG, "Creating VirtualDisplayPlatformView viewId=$viewId params=$creationParams")
        
        // creationParams에서 설정 추출
        creationParams?.let { params ->
            displayWidth = (params["width"] as? Int) ?: 1080
            displayHeight = (params["height"] as? Int) ?: 1920
            displayDpi = (params["dpi"] as? Int) ?: 320
        }
        
        // slotIndex 추출 (기본값: viewId 사용)
        // slotIndex는 Flutter의 widget.displayId (PIP 슬롯 번호: 1 또는 2)
        slotIndex = (creationParams?.get("slotIndex") as? Int) ?: viewId
        
        // AppViewSurface 생성 (slotIndex를 전달하여 VirtualDisplayManager 캐시 키로 사용)
        // 같은 slotIndex의 PlatformView가 재생성되어도 기존 VirtualDisplay 재사용
        appViewSurface = AppViewSurface(context, slotIndex, displayWidth, displayHeight, displayDpi)
        
        Log.i(TAG, "AppViewSurface created: viewId=$viewId slotIndex=$slotIndex ${displayWidth}x${displayHeight} dpi=$displayDpi")
        
        // PlatformViewTouchHandler에 등록 (viewId로 등록 - Flutter에서 viewId로 접근)
        PlatformViewTouchHandler.registerView(viewId, appViewSurface)
    }
    
    /**
     * displayId가 준비되었는지 확인
     * Flutter에서 getDisplayId 호출 시 사용
     */
    fun isDisplayReady(): Boolean = appViewSurface.displayId > 0

    override fun getView(): View = appViewSurface

    override fun dispose() {
        Log.d(TAG, "Disposing VirtualDisplayPlatformView viewId=$viewId displayId=${appViewSurface.displayId}")
        
        // PlatformViewTouchHandler에서 해제
        PlatformViewTouchHandler.unregisterView(viewId)
        
        // 핵심: dispose()에서 VirtualDisplay를 release하지 않음!
        // VirtualDisplayManager 캐시에 유지하여 PlatformView 재생성 시 재사용
        // AppViewSurface.dispose()는 내부적으로 Surface만 detach
        appViewSurface.dispose()
        
        Log.d(TAG, "VirtualDisplayPlatformView disposed (VirtualDisplay cached in VirtualDisplayManager)")
    }

    // ============================================
    // Flutter에서 호출 가능한 메서드들
    // ============================================

    fun getDisplayId(): Int = appViewSurface.displayId

    fun getDisplayWidth(): Int = appViewSurface.getDisplayWidth()
    
    fun getDisplayHeight(): Int = appViewSurface.getDisplayHeight()
    
    /**
     * 터치 이벤트 직접 주입 (원본 앱 z7/g.java의 b(MotionEvent) 방식)
     * PlatformViewTouchHandler 또는 VirtualDisplayViewFactory에서 호출
     */
    fun dispatchTouchEvent(motionEvent: MotionEvent) {
        appViewSurface.dispatchTouchToVirtualDisplay(motionEvent)
    }
    
    /**
     * 터치 오프셋 getter (원본 앱과 동일)
     */
    fun getTouchOffsetX(): Float = appViewSurface.touchOffsetX
    
    fun getTouchOffsetY(): Float = appViewSurface.touchOffsetY

    /**
     * VirtualDisplay 크기 조절
     */
    fun resize(width: Int, height: Int, dpi: Int) {
        Log.d(TAG, "Resize requested: ${width}x${height} dpi=$dpi")
        appViewSurface.setDisplaySize(width, height, dpi)
    }

    /**
     * 앱 실행
     */
    fun launchApp(packageName: String): Boolean {
        return appViewSurface.launchApp(packageName)
    }
    
    /**
     * Back 키 전송
     */
    fun sendBackKey() {
        appViewSurface.sendBackKey()
    }
    
    /**
     * 디스플레이 숨기기
     */
    fun hideDisplay() {
        appViewSurface.hideDisplay()
    }
    
    /**
     * 디스플레이 표시
     */
    fun showDisplay() {
        appViewSurface.showDisplay()
    }
    
    /**
     * 터치 활성화/비활성화
     */
    fun setTouchEnabled(enabled: Boolean) {
        appViewSurface.setTouchEnabled(enabled)
    }
}
