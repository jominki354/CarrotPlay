package android.test.settings.virtualdisplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface
import java.lang.reflect.Method

/**
 * VirtualDisplayManager - VirtualDisplay 싱글톤 캐시 관리
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
    
    // slotIndex -> VirtualDisplayInfo 캐시
    private val displayCache = mutableMapOf<Int, VirtualDisplayInfo>()
    
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
     * @param slotIndex PIP 슬롯 인덱스 (1 또는 2)
     * @param surface 연결할 Surface (null 가능)
     * @param width 디스플레이 너비
     * @param height 디스플레이 높이
     * @param density 디스플레이 밀도
     * @return VirtualDisplayInfo
     */
    fun getOrCreateDisplay(slotIndex: Int, surface: Surface?, width: Int, height: Int, density: Int): VirtualDisplayInfo? {
        val cached = displayCache[slotIndex]
        
        if (cached != null) {
            Log.d(TAG, "Reusing cached VirtualDisplay for slot=$slotIndex displayId=${cached.displayId}")
            
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
        return createNewDisplay(slotIndex, surface, width, height, density)
    }

    /**
     * 새 VirtualDisplay 생성
     */
    private fun createNewDisplay(slotIndex: Int, surface: Surface?, width: Int, height: Int, density: Int): VirtualDisplayInfo? {
        try {
            // DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | 
            // VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
            // VIRTUAL_DISPLAY_FLAG_TRUSTED (hidden, value=0x400)
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                       DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                       0x400  // VIRTUAL_DISPLAY_FLAG_TRUSTED
            
            val displayName = "CarrotPlay-PIP-$slotIndex"
            
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
            displayCache[slotIndex] = info
            
            Log.i(TAG, "VirtualDisplay created: slot=$slotIndex displayId=$displayId " +
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
    fun attachSurface(slotIndex: Int, surface: Surface, width: Int, height: Int): Boolean {
        val info = displayCache[slotIndex]
        if (info == null) {
            Log.w(TAG, "attachSurface: No VirtualDisplay for slot=$slotIndex")
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
        
        Log.d(TAG, "Surface attached: slot=$slotIndex displayId=${info.displayId}")
        return true
    }

    /**
     * Surface 분리 (VirtualDisplay는 유지)
     */
    fun detachSurface(slotIndex: Int) {
        val info = displayCache[slotIndex] ?: return
        
        setVirtualDisplayState(info.virtualDisplay, false)
        info.virtualDisplay.setSurface(null)
        info.currentSurface = null
        info.isActive = false
        
        Log.d(TAG, "Surface detached: slot=$slotIndex displayId=${info.displayId} (VirtualDisplay preserved)")
    }

    /**
     * VirtualDisplay 완전 해제
     */
    fun releaseDisplay(slotIndex: Int) {
        val info = displayCache.remove(slotIndex) ?: return
        
        try {
            info.virtualDisplay.release()
            Log.d(TAG, "VirtualDisplay released: slot=$slotIndex displayId=${info.displayId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release VirtualDisplay", e)
        }
    }

    /**
     * displayId 조회
     */
    fun getDisplayId(slotIndex: Int): Int {
        return displayCache[slotIndex]?.displayId ?: -1
    }

    /**
     * VirtualDisplayInfo 조회
     */
    fun getDisplayInfo(slotIndex: Int): VirtualDisplayInfo? {
        return displayCache[slotIndex]
    }

    /**
     * 캐시된 모든 VirtualDisplay 해제
     */
    fun releaseAll() {
        for (slotIndex in displayCache.keys.toList()) {
            releaseDisplay(slotIndex)
        }
        displayCache.clear()
        Log.d(TAG, "All VirtualDisplays released")
    }

    /**
     * 디버그: 캐시 상태 로깅
     */
    fun logCacheStatus() {
        Log.d(TAG, "=== VirtualDisplay Cache Status ===")
        Log.d(TAG, "Total cached: ${displayCache.size}")
        for ((slotIndex, info) in displayCache) {
            Log.d(TAG, "  slot=$slotIndex displayId=${info.displayId} " +
                  "active=${info.isActive} hasSurface=${info.currentSurface != null}")
        }
        Log.d(TAG, "====================================")
    }
}
