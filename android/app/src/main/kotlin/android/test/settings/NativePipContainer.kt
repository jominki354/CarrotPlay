package android.test.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator

/**
 * NativePipContainer - 완전 Native PIP 영역 컨테이너
 * 
 * Flutter 위에 WindowManager로 오버레이되어 터치를 직접 처리합니다.
 * 이 방식으로 Flutter ↔ Native 통신 오버헤드를 완전히 제거합니다.
 * 
 * 구조:
 * ┌─────────────────────────────────────┐
 * │  NativePipContainer (FrameLayout)   │
 * │  ┌─────────┐ ┼ ┌─────────┐         │
 * │  │  PIP 1  │ │ │  PIP 2  │         │
 * │  │ Surface │ │ │ Surface │         │
 * │  └─────────┘ │ └─────────┘         │
 * │              │                      │
 * │           Divider                   │
 * └─────────────────────────────────────┘
 */
@SuppressLint("ViewConstructor")
class NativePipContainer(
    context: Context,
    private var containerWidth: Int,
    private var containerHeight: Int
) : FrameLayout(context) {
    
    companion object {
        private const val TAG = "NativePipContainer"
        private const val DIVIDER_WIDTH = 8
        private const val MIN_RATIO = 0.25f
        private const val MAX_RATIO = 0.75f
        
        // 성능 측정용
        private var lastPerfLogTime = 0L
    }
    
    // PIP SurfaceView들
    private lateinit var pip1Surface: NativePipSurfaceView
    private lateinit var pip2Surface: NativePipSurfaceView
    
    // 구분선
    private lateinit var dividerView: View
    
    // 현재 비율 (왼쪽 PIP 비율)
    private var leftRatio = 0.5f
    
    // 스케일링 모드 (드래그/애니메이션 중 사용)
    // true: SurfaceView 스케일링으로 즉시 반응 (resize 호출 안 함)
    // false: 실제 LayoutParams 변경 + surfaceChanged에서 resize
    private var isScalingMode = false
    
    // 스케일링 기준점 (스케일링 시작 시점의 크기)
    private var scalingBasePip1Width = 0
    private var scalingBasePip2Width = 0
    
    // 고정 크기 모드: SurfaceView 크기는 초기값 유지, 스케일링만 사용
    private var fixedPip1Width = 0
    private var fixedPip2Width = 0
    private var isFixedSizeInitialized = false
    
    // 드래그 상태
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartRatio = 0f
    private var longPressTriggered = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!longPressTriggered) {
            longPressTriggered = true
            isDragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            updateDividerHighlight(true)
            Log.d(TAG, "Long press triggered - drag enabled")
        }
    }
    
    // 콜백
    var onRatioChanged: ((Float) -> Unit)? = null
    var onAppLaunchRequest: ((pipIndex: Int, packageName: String) -> Unit)? = null
    
    // VirtualDisplay 관리
    private val vdManager = VirtualDisplayManager.getInstance(context)
    
    init {
        Log.i(TAG, "NativePipContainer init: ${containerWidth}x${containerHeight}")
        setupViews()
    }
    
    private fun setupViews() {
        // 배경 투명
        setBackgroundColor(Color.TRANSPARENT)
        
        // PIP 1 (왼쪽)
        pip1Surface = NativePipSurfaceView(context, 0).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
        }
        addView(pip1Surface)
        
        // 구분선
        dividerView = View(context).apply {
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
            layoutParams = LayoutParams(DIVIDER_WIDTH, LayoutParams.MATCH_PARENT)
        }
        addView(dividerView)
        
        // PIP 2 (오른쪽)
        pip2Surface = NativePipSurfaceView(context, 1).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
        }
        addView(pip2Surface)
        
        // 초기 레이아웃 설정
        updateLayout()
    }
    
    /**
     * 비율에 따른 레이아웃 업데이트
     * 
     * 핵심 최적화: SurfaceView 크기는 고정, 스케일링 + 위치 이동만 사용
     * - LayoutParams 변경 없음 → requestLayout() 없음 → 레이아웃 재계산 없음
     * - scaleX, translationX만 변경 → GPU가 처리 → 즉시 반응
     * 
     * @param forceLayout true면 초기화 또는 컨테이너 크기 변경 시에만 LayoutParams 변경
     */
    private fun updateLayout(forceLayout: Boolean = false) {
        val startTime = System.nanoTime()
        
        val availableWidth = containerWidth - DIVIDER_WIDTH
        val targetPip1Width = (availableWidth * leftRatio).toInt()
        val targetPip2Width = availableWidth - targetPip1Width
        
        // 초기화 또는 강제 레이아웃 시에만 실제 크기 설정
        if (!isFixedSizeInitialized || forceLayout) {
            // 고정 크기 저장 (50:50 기준)
            fixedPip1Width = availableWidth / 2
            fixedPip2Width = availableWidth - fixedPip1Width
            
            // 실제 LayoutParams 설정 (1회만)
            (pip1Surface.layoutParams as LayoutParams).apply {
                width = fixedPip1Width
                height = containerHeight
                leftMargin = 0
            }
            pip1Surface.requestLayout()
            
            (pip2Surface.layoutParams as LayoutParams).apply {
                width = fixedPip2Width
                height = containerHeight
                leftMargin = fixedPip1Width + DIVIDER_WIDTH
            }
            pip2Surface.requestLayout()
            
            // 구분선
            (dividerView.layoutParams as LayoutParams).apply {
                leftMargin = fixedPip1Width
            }
            dividerView.requestLayout()
            
            isFixedSizeInitialized = true
            
            Log.i(TAG, "[PERF] Initial layout: fixed pip1=$fixedPip1Width, pip2=$fixedPip2Width")
        }
        
        // 스케일링으로 비율 표현 (LayoutParams 변경 없음!)
        val scale1 = targetPip1Width.toFloat() / fixedPip1Width
        val scale2 = targetPip2Width.toFloat() / fixedPip2Width
        
        // PIP1: 왼쪽 기준점, scaleX만 변경
        pip1Surface.pivotX = 0f
        pip1Surface.pivotY = 0f
        pip1Surface.scaleX = scale1
        
        // PIP2: 오른쪽 기준점, scaleX + 위치 이동
        pip2Surface.pivotX = fixedPip2Width.toFloat()
        pip2Surface.pivotY = 0f
        pip2Surface.scaleX = scale2
        // PIP2 위치 조정: 목표 위치 - 고정 위치
        val pip2TargetLeft = targetPip1Width + DIVIDER_WIDTH
        val pip2FixedLeft = fixedPip1Width + DIVIDER_WIDTH
        pip2Surface.translationX = (pip2TargetLeft - pip2FixedLeft).toFloat()
        
        // 구분선 위치 (translationX로 이동)
        dividerView.translationX = (targetPip1Width - fixedPip1Width).toFloat()
        
        val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
        
        // 성능 로그 (100ms마다 출력하여 로그 폭주 방지)
        val now = System.currentTimeMillis()
        if (now - lastPerfLogTime > 100) {
            Log.d(TAG, "[PERF] updateLayout: ratio=$leftRatio, scale1=${"%.3f".format(scale1)}, scale2=${"%.3f".format(scale2)}, elapsed=${"%.2f".format(elapsed)}ms")
            lastPerfLogTime = now
        }
    }
    
    /**
     * 스케일링 모드 시작 (드래그/애니메이션 시작 시 호출)
     * 이제는 항상 스케일링 모드이므로 로그만 출력
     */
    private fun enterScalingMode() {
        if (isScalingMode) return
        isScalingMode = true
        Log.d(TAG, "[PERF] Entered scaling mode")
    }
    
    /**
     * 스케일링 모드 종료 (손 뗌/애니메이션 완료 시 호출)
     * 이제는 LayoutParams 변경 없이 스케일링 유지
     */
    private fun exitScalingMode() {
        if (!isScalingMode) return
        isScalingMode = false
        Log.d(TAG, "[PERF] Exited scaling mode: final ratio=$leftRatio")
    }
    
    /**
     * 현재 비율 반환
     */
    fun getCurrentRatio(): Float = leftRatio
    
    /**
     * 컨테이너 크기 업데이트 (방법 B: 동적 크기 조절)
     */
    fun updateContainerSize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        
        containerWidth = newWidth
        containerHeight = newHeight
        
        // 컨테이너 크기 변경 시 고정 크기 재계산 필요
        isFixedSizeInitialized = false
        updateLayout(forceLayout = true)
        
        Log.d(TAG, "[PERF] Container size updated: ${newWidth}x${newHeight}")
    }
    
    /**
     * 구분선 하이라이트 효과
     */
    private fun updateDividerHighlight(highlight: Boolean) {
        val color = if (highlight) {
            Color.parseColor("#FFFF6B00") // 캐럿 오렌지
        } else {
            Color.parseColor("#40FFFFFF")
        }
        dividerView.setBackgroundColor(color)
    }
    
    /**
     * 비율 설정 (외부에서 호출) - 즉시 적용
     */
    fun setRatio(ratio: Float) {
        leftRatio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        updateLayout()  // 스케일링만 (LayoutParams 변경 없음)
    }
    
    // 애니메이션 참조 (중복 방지)
    private var ratioAnimator: ValueAnimator? = null
    
    // VirtualDisplay resize debounce (race condition 방지)
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var pendingResizeRunnable: Runnable? = null
    private var isResizing = false
    
    /**
     * 비율 설정 (애니메이션 적용) - 부드러운 전환
     * 
     * 스케일링만 사용하여 60fps 즉시 반응
     * LayoutParams 변경 없음, VirtualDisplay resize 없음
     * 
     * @param ratio 목표 비율
     * @param durationMs 애니메이션 시간 (기본 150ms)
     * @param onComplete 완료 콜백
     */
    fun setRatioAnimated(ratio: Float, durationMs: Long = 150, onComplete: (() -> Unit)? = null) {
        val animStartTime = System.nanoTime()
        val targetRatio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        
        // 같은 비율이면 무시
        if (kotlin.math.abs(leftRatio - targetRatio) < 0.01f) {
            onComplete?.invoke()
            return
        }
        
        // 기존 애니메이션 취소
        ratioAnimator?.cancel()
        
        enterScalingMode()
        
        val startRatio = leftRatio
        Log.i(TAG, "[PERF] Animation START: $startRatio -> $targetRatio")
        
        ratioAnimator = ValueAnimator.ofFloat(startRatio, targetRatio).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            
            addUpdateListener { animation ->
                leftRatio = animation.animatedValue as Float
                updateLayout()  // 스케일링만 (LayoutParams 변경 없음)
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    exitScalingMode()
                    val elapsed = (System.nanoTime() - animStartTime) / 1_000_000.0
                    Log.i(TAG, "[PERF] Animation END: ratio=$targetRatio, total=${"%.1f".format(elapsed)}ms")
                    onComplete?.invoke()
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    exitScalingMode()
                }
            })
            
            start()
        }
    }
    
    /**
     * VirtualDisplay 크기 조정 (비동기 - UI 스레드 블로킹 방지)
     * Handler.post로 다음 메시지 루프에서 실행
     */
    private fun resizeVirtualDisplays() {
        val availableWidth = containerWidth - DIVIDER_WIDTH
        val pip1Width = (availableWidth * leftRatio).toInt()
        val pip2Width = availableWidth - pip1Width
        
        // 비동기 실행 (UI 바로 반환)
        resizeHandler.post {
            pip1Surface.resizeDisplay(pip1Width, containerHeight)
            pip2Surface.resizeDisplay(pip2Width, containerHeight)
        }
    }
    
    /**
     * VirtualDisplay 크기 조정 (Debounce + 싱글 실행 보장)
     * 빠른 연속 호출 시 마지막 요청만 실행하여 race condition 방지
     */
    private fun resizeVirtualDisplaysAsync() {
        // 이전 예약된 resize 취소
        pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
        
        // 현재 resize 진행 중이면 100ms 후 재시도 예약
        if (isResizing) {
            pendingResizeRunnable = Runnable { resizeVirtualDisplaysAsync() }
            resizeHandler.postDelayed(pendingResizeRunnable!!, 100)
            return
        }
        
        val availableWidth = containerWidth - DIVIDER_WIDTH
        val pip1Width = (availableWidth * leftRatio).toInt()
        val pip2Width = availableWidth - pip1Width
        
        // Debounce: 50ms 후 실행 (빠른 연속 호출 병합)
        pendingResizeRunnable = Runnable {
            isResizing = true
            try {
                pip1Surface.resizeDisplay(pip1Width, containerHeight)
                pip2Surface.resizeDisplay(pip2Width, containerHeight)
                Log.d(TAG, "VirtualDisplay resized: pip1=${pip1Width}px, pip2=${pip2Width}px")
            } finally {
                isResizing = false
            }
        }
        resizeHandler.postDelayed(pendingResizeRunnable!!, 50)
    }
    
    /**
     * PIP에 앱 실행
     */
    fun launchApp(pipIndex: Int, packageName: String): Boolean {
        return when (pipIndex) {
            0 -> pip1Surface.launchApp(packageName)
            1 -> pip2Surface.launchApp(packageName)
            else -> false
        }
    }
    
    /**
     * PIP 정보 가져오기
     */
    fun getPipDisplayId(pipIndex: Int): Int {
        return when (pipIndex) {
            0 -> pip1Surface.displayId
            1 -> pip2Surface.displayId
            else -> -1
        }
    }
    
    fun getPipCurrentPackage(pipIndex: Int): String? {
        return when (pipIndex) {
            0 -> pip1Surface.currentPackage
            1 -> pip2Surface.currentPackage
            else -> null
        }
    }
    
    /**
     * PIP에 뒤로가기 키 전송
     */
    fun sendBackKey(pipIndex: Int) {
        when (pipIndex) {
            0 -> pip1Surface.sendBackKey()
            1 -> pip2Surface.sendBackKey()
        }
    }
    
    /**
     * 구분선 터치 처리
     * 
     * 혼용 방식:
     * - 드래그 중: 스케일링으로 즉시 반응 (60fps)
     * - 손 뗌: 실제 LayoutParams 변경 → surfaceChanged에서 resize
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        // 구분선 영역 확인 (터치 허용 범위 확장)
        val dividerLeft = (containerWidth - DIVIDER_WIDTH) * leftRatio - 20
        val dividerRight = dividerLeft + DIVIDER_WIDTH + 40
        val isOnDivider = x >= dividerLeft && x <= dividerRight
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isOnDivider) {
                    dragStartX = x
                    dragStartRatio = leftRatio
                    longPressTriggered = false
                    
                    // 1초 롱프레스 타이머 시작
                    longPressHandler.postDelayed(longPressRunnable, 1000)
                    return true
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // 비율 계산
                    val deltaX = x - dragStartX
                    val deltaRatio = deltaX / (containerWidth - DIVIDER_WIDTH)
                    val newRatio = (dragStartRatio + deltaRatio).coerceIn(MIN_RATIO, MAX_RATIO)
                    
                    // 5% 단위로 스냅
                    val snappedRatio = (newRatio * 20).toInt() / 20f
                    
                    if (snappedRatio != leftRatio) {
                        leftRatio = snappedRatio
                        // 스케일링만 (LayoutParams 변경 없음)
                        updateLayout()
                    }
                    return true
                } else if (longPressHandler.hasCallbacks(longPressRunnable)) {
                    // 롱프레스 대기 중 이동 감지
                    val moveDistance = kotlin.math.abs(x - dragStartX)
                    if (moveDistance > 10) {
                        // 이동이 감지되면 롱프레스 취소
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                
                if (isDragging) {
                    isDragging = false
                    longPressTriggered = false
                    updateDividerHighlight(false)
                    
                    // 콜백으로 최종 비율 전달
                    onRatioChanged?.invoke(leftRatio)
                    
                    Log.d(TAG, "[PERF] Drag ended: final ratio=$leftRatio")
                    return true
                }
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * 자식 뷰 터치 이벤트 가로채기 방지
     * PIP 영역 터치는 각 SurfaceView에서 직접 처리
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        
        // 구분선 영역만 가로채기
        val dividerLeft = (containerWidth - DIVIDER_WIDTH) * leftRatio - 20
        val dividerRight = dividerLeft + DIVIDER_WIDTH + 40
        
        return x >= dividerLeft && x <= dividerRight
    }
    
    /**
     * 리소스 정리
     */
    fun release() {
        pip1Surface.release()
        pip2Surface.release()
        Log.d(TAG, "NativePipContainer released")
    }
}

/**
 * NativePipSurfaceView - 개별 PIP SurfaceView
 * 
 * VirtualDisplay를 직접 관리하고 터치를 즉시 주입합니다.
 * AppViewSurface와 유사하지만 더 가볍고 빠릅니다.
 */
@SuppressLint("ViewConstructor")
class NativePipSurfaceView(
    context: Context,
    private val pipIndex: Int // 0 = 왼쪽, 1 = 오른쪽
) : SurfaceView(context), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "NativePipSurface"
        
        // 리플렉션 캐시
        private var sSetDisplayIdMotion: java.lang.reflect.Method? = null
        private var sSetDisplayIdKey: java.lang.reflect.Method? = null
        private var sReflectionInitialized = false
        
        @SuppressLint("PrivateApi")
        private fun initReflection() {
            if (sReflectionInitialized) return
            
            try {
                sSetDisplayIdMotion = MotionEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdMotion?.isAccessible = true
            } catch (e: Exception) {
                Log.e(TAG, "MotionEvent.setDisplayId not found", e)
            }
            
            try {
                sSetDisplayIdKey = KeyEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                sSetDisplayIdKey?.isAccessible = true
            } catch (e: Exception) {
                Log.e(TAG, "KeyEvent.setDisplayId not found", e)
            }
            
            sReflectionInitialized = true
        }
    }
    
    private val vdManager = VirtualDisplayManager.getInstance(context)
    
    var displayId = -1
        private set
    var currentPackage: String? = null
        private set
    
    private var displayWidth = 800
    private var displayHeight = 600
    private var displayDensity = 320
    
    private var surfaceReady = false
    
    // 터치 상태 추적
    private var touchDownTime = 0L
    
    init {
        initReflection()
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        
        // SurfaceView Z-order 설정
        // WindowManager Panel 안에서는 setZOrderOnTop을 사용해야 함
        // 별도 Window이므로 FlutterView와 충돌하지 않음
        setZOrderOnTop(true)
        
        // 터치 이벤트 직접 처리
        setOnTouchListener { _, event -> handleTouchEvent(event) }
        
        Log.d(TAG, "NativePipSurfaceView init: pipIndex=$pipIndex")
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated pipIndex=$pipIndex")
        surfaceReady = true
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: pipIndex=$pipIndex ${width}x${height} (prev: ${displayWidth}x${displayHeight})")
        
        val prevWidth = displayWidth
        val prevHeight = displayHeight
        displayWidth = width
        displayHeight = height
        
        val surface = holder.surface
        if (surface == null || !surface.isValid) {
            Log.e(TAG, "surfaceChanged: surface invalid")
            return
        }
        
        val slotIndex = 100 + pipIndex
        
        // 이미 VirtualDisplay가 있으면 - 크기 변경 시 resize 하지 않음!
        // 대신 SurfaceView가 자동으로 스케일링 처리 (하드웨어 가속)
        // 이렇게 하면 앱에 Configuration 변경이 전달되지 않아 렉 없음
        if (displayId > 0) {
            // VirtualDisplay 해상도는 그대로, Surface만 재연결
            // 원본 앱처럼 setSurface 호출
            vdManager.getVirtualDisplay(displayId)?.setSurface(surface)
            Log.i(TAG, "Surface reconnected (no resize): pipIndex=$pipIndex displayId=$displayId")
            return
        }
        
        // VirtualDisplay 생성 (초기 1회만)
        val info = vdManager.getOrCreateDisplay(slotIndex, surface, width, height, displayDensity)
        if (info != null) {
            displayId = info.displayId
            Log.i(TAG, "VirtualDisplay ready: pipIndex=$pipIndex displayId=$displayId")
        } else {
            Log.e(TAG, "Failed to create VirtualDisplay for pipIndex=$pipIndex")
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed pipIndex=$pipIndex")
        surfaceReady = false
        
        // Surface만 분리 (VirtualDisplay 캐시 유지)
        val slotIndex = 100 + pipIndex
        vdManager.detachSurface(slotIndex)
    }
    
    /**
     * 터치 이벤트 직접 처리 (원본 앱 방식)
     * Flutter 경유 없이 즉시 VirtualDisplay에 주입
     */
    @SuppressLint("PrivateApi")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (displayId < 0) return false
        
        val action = event.actionMasked
        
        // DOWN 시 시간 저장
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = SystemClock.uptimeMillis()
        }
        
        // 이벤트 복사 및 displayId 설정
        val injectedEvent = MotionEvent.obtain(event)
        
        try {
            sSetDisplayIdMotion?.invoke(injectedEvent, displayId)
            
            // 즉시 주입 (ASYNC 모드)
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(injectedEvent, 0)
            
            // UP/CANCEL 시에만 sync (성능 최적화)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                try {
                    WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true)
                } catch (e: Exception) {
                    // sync 실패해도 무시
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Touch injection failed", e)
        } finally {
            injectedEvent.recycle()
        }
        
        return true
    }
    
    /**
     * 앱 실행
     * - 같은 앱이 이미 해당 display에서 실행 중이면 재사용
     * - 다른 앱이면 기존 앱 종료 후 새 앱 실행
     */
    fun launchApp(packageName: String): Boolean {
        if (displayId < 0) {
            Log.e(TAG, "launchApp: displayId not ready")
            return false
        }
        
        // 같은 앱이 이미 실행 중이면 Task를 앞으로 가져오기만 함
        if (currentPackage == packageName) {
            Log.i(TAG, "Same app already running, bringing to front: $packageName")
            val cmd = "am start --display $displayId --windowingMode 1 --activity-single-top --activity-reorder-to-front -n \"$(cmd package resolve-activity --brief $packageName | tail -n 1)\" 2>/dev/null || am start --display $displayId --windowingMode 1 -S $packageName"
            Thread {
                // 단순히 해당 display의 Task를 활성화
                RootUtils.executeCommandAsync("am stack move-task $(am stack list | grep -E \"taskId=[0-9]+.*$packageName\" | head -1 | sed 's/.*taskId=\\([0-9]*\\).*/\\1/') 0 true 2>/dev/null")
            }.start()
            return true
        }
        
        // 다른 앱으로 전환: 기존 앱이 있으면 종료
        if (currentPackage != null && currentPackage != packageName) {
            Log.i(TAG, "Switching app: $currentPackage -> $packageName, stopping old app on display")
            // 기존 앱의 해당 display Task만 종료 (전체 앱 종료 아님)
            Thread {
                RootUtils.executeCommandAsync("am stack remove $(am stack list | grep -E \"displayId=$displayId.*$currentPackage\" | head -1 | sed 's/.*stackId=\\([0-9]*\\).*/\\1/') 2>/dev/null")
            }.start()
            Thread.sleep(50) // 짧은 대기
        }
        
        currentPackage = packageName
        
        // ROOT 명령으로 실행
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val component = launchIntent?.component?.flattenToShortString() ?: return false
        val escapedComponent = component.replace("$", "\\$")
        
        // --activity-single-top: 기존 인스턴스 재사용
        // --activity-clear-top: 위의 Activity들 제거하고 기존 인스턴스로
        // --activity-reorder-to-front: 기존 Task를 앞으로
        val cmd = "am start -n \"$escapedComponent\" --display $displayId --windowingMode 1 --activity-single-top --activity-clear-top --activity-reorder-to-front"
        
        Thread {
            RootUtils.executeCommandAsync(cmd)
        }.start()
        
        Log.i(TAG, "Launched $packageName on pipIndex=$pipIndex displayId=$displayId")
        return true
    }
    
    /**
     * VirtualDisplay 크기 조정
     */
    fun resizeDisplay(width: Int, height: Int) {
        if (displayId < 0) return
        
        displayWidth = width
        displayHeight = height
        
        val slotIndex = 100 + pipIndex
        vdManager.resizeVirtualDisplay(displayId, width, height, displayDensity)
        
        Log.d(TAG, "Resized display: pipIndex=$pipIndex ${width}x${height}")
    }
    
    /**
     * 뒤로가기 키 전송
     */
    fun sendBackKey() {
        if (displayId < 0) return
        
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        
        try {
            sSetDisplayIdKey?.invoke(downEvent, displayId)
            sSetDisplayIdKey?.invoke(upEvent, displayId)
            
            val inputManager = InputManager.getInstance()
            inputManager.injectInputEvent(downEvent, 0)
            inputManager.injectInputEvent(upEvent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "sendBackKey failed", e)
        }
    }
    
    /**
     * 리소스 정리
     */
    fun release() {
        val slotIndex = 100 + pipIndex
        vdManager.releaseDisplay(slotIndex)
        displayId = -1
        currentPackage = null
        Log.d(TAG, "NativePipSurfaceView released: pipIndex=$pipIndex")
    }
}
