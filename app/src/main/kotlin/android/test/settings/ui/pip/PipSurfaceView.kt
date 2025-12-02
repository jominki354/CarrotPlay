package android.test.settings.ui.pip

import android.test.settings.virtualdisplay.AppViewSurface
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import android.test.settings.ui.theme.AppColors
import android.test.settings.ui.theme.AppDimens

/**
 * PipSurfaceView - VirtualDisplay를 Compose에 통합하는 컴포넌트
 * 
 * @param slotIndex PIP 슬롯 인덱스 (1 또는 2)
 * @param modifier Modifier
 * @param onDisplayReady VirtualDisplay가 준비되면 호출 (displayId 전달)
 * @param onSurfaceCreated AppViewSurface가 생성되면 호출
 */
@Composable
fun PipSurfaceView(
    slotIndex: Int,
    modifier: Modifier = Modifier,
    onDisplayReady: ((Int) -> Unit)? = null,
    onSurfaceCreated: ((AppViewSurface) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // AppViewSurface 인스턴스 유지
    var appViewSurface by remember { mutableStateOf<AppViewSurface?>(null) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimens.RadiusLarge))
            .border(
                width = AppDimens.PipBorder,
                color = AppColors.WhiteAlpha20,
                shape = RoundedCornerShape(AppDimens.RadiusLarge)
            )
            .background(AppColors.GlassGrey.copy(alpha = 0.3f))
    ) {
        AndroidView(
            factory = { ctx ->
                Log.d("PipSurfaceView", "Creating AppViewSurface for slot=$slotIndex")
                // 초기값은 surfaceChanged에서 실제 크기로 업데이트됨
                AppViewSurface(ctx).apply {
                    setSlotIndex(slotIndex)
                    setOnDisplayReadyListener(object : AppViewSurface.OnDisplayReadyListener {
                        override fun onDisplayReady(displayId: Int) {
                            Log.d("PipSurfaceView", "Display ready: slot=$slotIndex displayId=$displayId")
                            onDisplayReady?.invoke(displayId)
                        }
                    })
                    appViewSurface = this
                    onSurfaceCreated?.invoke(this)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(AppDimens.RadiusLarge)),
            update = { view ->
                // AndroidView가 리컴포지션될 때 호출됨
            },
            onRelease = { view ->
                Log.d("PipSurfaceView", "Releasing AppViewSurface for slot=$slotIndex")
                view.dispose()
            }
        )
    }
    
    // Cleanup
    DisposableEffect(slotIndex) {
        onDispose {
            Log.d("PipSurfaceView", "Disposing PipSurfaceView for slot=$slotIndex")
            appViewSurface?.dispose()
        }
    }
}

/**
 * PipSurfaceView의 상태를 관리하는 클래스
 */
class PipSurfaceState {
    var appViewSurface: AppViewSurface? = null
        internal set
    
    var displayId: Int = -1
        internal set
    
    var isReady: Boolean = false
        internal set
    
    fun launchApp(packageName: String): Boolean {
        return appViewSurface?.launchApp(packageName) ?: false
    }
    
    fun sendBackKey() {
        appViewSurface?.sendBackKey()
    }
    
    fun sendHomeKey() {
        appViewSurface?.sendHomeKey()
    }
}

/**
 * PipSurfaceState를 기억하는 remember 함수
 */
@Composable
fun rememberPipSurfaceState(): PipSurfaceState {
    return remember { PipSurfaceState() }
}
