package android.test.settings

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import java.lang.reflect.Method

/**
 * VirtualDisplayManager - VirtualDisplay 싱글톤 캐시 관리
 * 
 * PlatformView가 Flutter에 의해 재생성되어도 기존 VirtualDisplay를 재사용합니다.
 * viewId 별로 VirtualDisplay를 캐시하고, Surface만 교체합니다.
 * 
 * 핵심:
 * - getOrCreateDisplay(): 캐시된 VirtualDisplay 반환 또는 새로 생성
 * - attachSurface(): 기존 VirtualDisplay에 새 Surface 연결
 * - detachSurface(): VirtualDisplay를 유지하면서 Surface만 분리
 */
class VirtualDisplayManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VDManager"
        
        @Volatile
        private var instance: VirtualDisplayManager? = null
        
        @JvmStatic
        fun getInstance(context: Context): VirtualDisplayManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualDisplayManager(context.applicationContext).also { instance = it }
            }
        }
        
        // Reflection 캐시
        private var sSetDisplayState: Method? = null
        private var sReflectionInitialized = false
        
        @Synchronized
        private fun initReflection() {
            if (sReflectionInitialized) return
            
            try {
                sSetDisplayState = VirtualDisplay::class.java.getDeclaredMethod("setDisplayState", Boolean::class.javaPrimitiveType)
                sSetDisplayState?.isAccessible = true
                Log.d(TAG, "Reflection: setDisplayState initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Reflection: setDisplayState not found", e)
            }
            
            sReflectionInitialized = true
        }
    }

    /**
     * VirtualDisplay 정보 저장용 클래스
     * Java에서 접근 가능하도록 @JvmField 사용
     */
    class VirtualDisplayInfo(
        @JvmField val virtualDisplay: VirtualDisplay,
        @JvmField val displayId: Int,
        @JvmField var width: Int,
        @JvmField var height: Int,
        @JvmField var density: Int,
        @JvmField var isActive: Boolean = true,
        @JvmField var currentSurface: Surface? = null
    )

    private val displayManager: DisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    
    // viewId -> VirtualDisplayInfo 캐시
    private val displayCache = mutableMapOf<Int, VirtualDisplayInfo>()
    
    // 기존 호환성: displayId -> VirtualDisplay
    private val virtualDisplays = mutableMapOf<Int, VirtualDisplay>()
    
    init {
        initReflection()
    }

    /**
     * VirtualDisplay 상태 설정 (ON/OFF)
     */
    private fun setVirtualDisplayState(vd: VirtualDisplay, on: Boolean) {
        sSetDisplayState?.let { method ->
            try {
                method.invoke(vd, on)
                Log.d(TAG, "setDisplayState($on) success")
            } catch (e: Exception) {
                Log.e(TAG, "setDisplayState failed", e)
            }
        }
    }

    /**
     * 캐시된 VirtualDisplay 반환 또는 새로 생성
     * 
     * @param viewId PlatformView viewId (슬롯 식별자)
     * @param surface 연결할 Surface (null 가능)
     * @param width 디스플레이 너비
     * @param height 디스플레이 높이
     * @param density 디스플레이 밀도
     * @return VirtualDisplayInfo
     */
    fun getOrCreateDisplay(viewId: Int, surface: Surface?, width: Int, height: Int, density: Int): VirtualDisplayInfo? {
        val cached = displayCache[viewId]
        
        if (cached != null) {
            // 캐시된 VirtualDisplay가 있음 - Surface만 연결
            Log.d(TAG, "Reusing cached VirtualDisplay for viewId=$viewId displayId=${cached.displayId}")
            
            // 크기가 변경되었으면 resize
            if (cached.width != width || cached.height != height || cached.density != density) {
                cached.virtualDisplay.resize(width, height, density)
                cached.width = width
                cached.height = height
                cached.density = density
                Log.d(TAG, "Resized VirtualDisplay to ${width}x$height")
            }
            
            // Surface 연결
            if (surface != null && surface.isValid) {
                cached.virtualDisplay.setSurface(surface)
                cached.currentSurface = surface
                setVirtualDisplayState(cached.virtualDisplay, true)
                cached.isActive = true
                Log.d(TAG, "Surface attached to cached VirtualDisplay displayId=${cached.displayId}")
            }
            
            return cached
        }
        
        // 새 VirtualDisplay 생성
        return createNewDisplay(viewId, surface, width, height, density)
    }

    /**
     * 새 VirtualDisplay 생성
     */
    private fun createNewDisplay(viewId: Int, surface: Surface?, width: Int, height: Int, density: Int): VirtualDisplayInfo? {
        try {
            // DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | 
            // VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
            // VIRTUAL_DISPLAY_FLAG_TRUSTED (hidden, value=0x400)
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                       DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                       0x400  // VIRTUAL_DISPLAY_FLAG_TRUSTED
            
            val displayName = "AppViewDisplay-$viewId"
            
            val vd = displayManager.createVirtualDisplay(
                displayName,
                width,
                height,
                density,
                surface,
                flags
            )
            
            if (vd == null) {
                Log.e(TAG, "createVirtualDisplay returned null")
                return null
            }
            
            val displayId = vd.display.displayId
            setVirtualDisplayState(vd, true)
            
            val info = VirtualDisplayInfo(vd, displayId, width, height, density).apply {
                currentSurface = surface
            }
            displayCache[viewId] = info
            virtualDisplays[displayId] = vd  // 기존 호환성
            
            Log.i(TAG, "VirtualDisplay created: viewId=$viewId displayId=$displayId " +
                  "size=${width}x$height dpi=$density")
            
            return info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            return null
        }
    }

    /**
     * Surface 연결 (기존 VirtualDisplay에)
     */
    fun attachSurface(viewId: Int, surface: Surface, width: Int, height: Int): Boolean {
        val info = displayCache[viewId]
        if (info == null) {
            Log.w(TAG, "attachSurface: No VirtualDisplay for viewId=$viewId")
            return false
        }
        
        if (!surface.isValid) {
            Log.w(TAG, "attachSurface: Invalid surface")
            return false
        }
        
        // 크기가 변경되었으면 resize
        if (info.width != width || info.height != height) {
            info.virtualDisplay.resize(width, height, info.density)
            info.width = width
            info.height = height
            Log.d(TAG, "Resized VirtualDisplay to ${width}x$height")
        }
        
        info.virtualDisplay.setSurface(surface)
        info.currentSurface = surface
        setVirtualDisplayState(info.virtualDisplay, true)
        info.isActive = true
        
        Log.d(TAG, "Surface attached: viewId=$viewId displayId=${info.displayId}")
        return true
    }

    /**
     * Surface 분리 (VirtualDisplay는 유지)
     */
    fun detachSurface(viewId: Int) {
        val info = displayCache[viewId] ?: return
        
        setVirtualDisplayState(info.virtualDisplay, false)
        info.virtualDisplay.setSurface(null)
        info.currentSurface = null
        info.isActive = false
        
        Log.d(TAG, "Surface detached: viewId=$viewId displayId=${info.displayId} (VirtualDisplay preserved)")
    }

    /**
     * VirtualDisplay 완전 해제
     */
    fun releaseDisplay(viewId: Int) {
        val info = displayCache.remove(viewId) ?: return
        virtualDisplays.remove(info.displayId)
        
        try {
            info.virtualDisplay.release()
            Log.d(TAG, "VirtualDisplay released: viewId=$viewId displayId=${info.displayId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release VirtualDisplay", e)
        }
    }

    /**
     * displayId 조회
     */
    fun getDisplayId(viewId: Int): Int {
        return displayCache[viewId]?.displayId ?: -1
    }

    /**
     * VirtualDisplayInfo 조회
     */
    fun getDisplayInfo(viewId: Int): VirtualDisplayInfo? {
        return displayCache[viewId]
    }

    // ============================================
    // 기존 호환성 메서드 (displayId 기반)
    // ============================================

    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface,
        flags: Int
    ): VirtualDisplay? {
        val virtualDisplay = displayManager.createVirtualDisplay(
            name,
            width,
            height,
            densityDpi,
            surface,
            flags
        )
        
        if (virtualDisplay != null) {
            virtualDisplays[virtualDisplay.display.displayId] = virtualDisplay
        }
        
        return virtualDisplay
    }

    /**
     * VirtualDisplay 크기/DPI 조절 (원본 앱 z7/f.java 방식)
     */
    fun resizeVirtualDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int): Boolean {
        val virtualDisplay = virtualDisplays[displayId]
        if (virtualDisplay == null) {
            Log.e(TAG, "VirtualDisplay not found: $displayId")
            return false
        }
        
        return try {
            Log.d(TAG, "Resizing display $displayId to ${width}x$height @ ${densityDpi}dpi")
            virtualDisplay.resize(width, height, densityDpi)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize VirtualDisplay", e)
            false
        }
    }

    fun releaseVirtualDisplay(displayId: Int) {
        virtualDisplays[displayId]?.release()
        virtualDisplays.remove(displayId)
    }
    
    fun getVirtualDisplay(displayId: Int): VirtualDisplay? {
        return virtualDisplays[displayId]
    }

    /**
     * 캐시된 모든 VirtualDisplay 해제
     */
    fun releaseAll() {
        for (viewId in displayCache.keys.toList()) {
            releaseDisplay(viewId)
        }
        displayCache.clear()
        virtualDisplays.clear()
        Log.d(TAG, "All VirtualDisplays released")
    }

    /**
     * 디버그: 캐시 상태 로깅
     */
    fun logCacheStatus() {
        Log.d(TAG, "=== VirtualDisplay Cache Status ===")
        Log.d(TAG, "Total cached: ${displayCache.size}")
        for ((viewId, info) in displayCache) {
            Log.d(TAG, "  viewId=$viewId displayId=${info.displayId} " +
                  "active=${info.isActive} hasSurface=${info.currentSurface != null}")
        }
        Log.d(TAG, "====================================")
    }
}
