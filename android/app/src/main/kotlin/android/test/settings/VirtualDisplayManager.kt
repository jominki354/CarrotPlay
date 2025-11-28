package android.test.settings

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface

class VirtualDisplayManager(private val context: Context) {

    companion object {
        private const val TAG = "VirtualDisplayManager"
    }

    private val displayManager: DisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val virtualDisplays = mutableMapOf<Int, VirtualDisplay>()

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
     * 
     * @param displayId VirtualDisplay의 displayId
     * @param width 새 너비
     * @param height 새 높이
     * @param densityDpi 새 DPI
     * @return 성공 여부
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
}
