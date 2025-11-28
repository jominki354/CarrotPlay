package android.test.settings

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface

class VirtualDisplayManager(private val context: Context) {

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

    fun releaseVirtualDisplay(displayId: Int) {
        virtualDisplays[displayId]?.release()
        virtualDisplays.remove(displayId)
    }
    
    fun getVirtualDisplay(displayId: Int): VirtualDisplay? {
        return virtualDisplays[displayId]
    }
}
