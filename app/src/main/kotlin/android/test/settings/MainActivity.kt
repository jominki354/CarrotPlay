package android.test.settings

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.test.settings.ui.theme.*
import android.test.settings.ui.pip.PipSurfaceView
import android.test.settings.ui.applist.AppListDialog
import android.test.settings.virtualdisplay.AppViewSurface
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            CarrotPlayTheme {
                HomeScreen()
            }
        }
        
        // setContent 이후에 전체 화면 모드 설정
        window.decorView.post {
            hideSystemUI()
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                // 네비게이션 바 숨김
                controller.hide(WindowInsets.Type.navigationBars())
                // 스와이프해도 네비게이션 바가 나타나지 않도록 설정
                controller.systemBarsBehavior = 
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 (API 29)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}

// 프리셋 비율 정의
object RatioPresets {
    val PRESET_1 = 0.3f  // PIP1 30% : PIP2 70%
    val PRESET_2 = 0.5f  // PIP1 50% : PIP2 50%
    val PRESET_3 = 0.7f  // PIP1 70% : PIP2 30%
    
    const val MIN_RATIO = 0.2f
    const val MAX_RATIO = 0.8f
    const val SNAP_UNIT = 0.05f  // 5% 단위 스냅
    
    // 5% 단위로 스냅
    fun snapToUnit(ratio: Float): Float {
        return (ratio / SNAP_UNIT).toInt() * SNAP_UNIT
    }
}

@Composable
fun HomeScreen() {
    var targetRatio by remember { mutableFloatStateOf(0.5f) }
    var isDragging by remember { mutableStateOf(false) }
    val view = LocalView.current
    
    // 드래그 중에는 애니메이션 없이 즉시 반영, 프리셋 클릭 시에만 애니메이션
    val displayRatio = if (isDragging) {
        targetRatio
    } else {
        animateFloatAsState(
            targetValue = targetRatio,
            animationSpec = tween(durationMillis = 300),
            label = "ratio"
        ).value
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.MidnightBlack)
            .onGloballyPositioned { coordinates ->
                // 전체 하단 영역에 시스템 제스처 제외 설정
                val size = coordinates.size
                val bottomExclusionHeight = 60  // 하단 60px 영역
                val exclusionRect = Rect(
                    0,
                    size.height - bottomExclusionHeight,
                    size.width,
                    size.height
                )
                view.systemGestureExclusionRects = listOf(exclusionRect)
            }
    ) {
        // Dock
        Dock(
            onPresetClick = { presetIndex ->
                targetRatio = when (presetIndex) {
                    0 -> RatioPresets.PRESET_1
                    1 -> RatioPresets.PRESET_2
                    2 -> RatioPresets.PRESET_3
                    else -> RatioPresets.PRESET_2
                }
            },
            currentRatio = displayRatio,
            modifier = Modifier
                .fillMaxHeight()
                .width(AppDimens.DockWidth)
        )
        
        // PIP 영역
        PipArea(
            ratio = displayRatio,
            onRatioChange = { newRatio -> 
                targetRatio = newRatio 
            },
            onDragStateChange = { dragging ->
                isDragging = dragging
            },
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )
    }
}

@Composable
fun Dock(
    onPresetClick: (Int) -> Unit,
    currentRatio: Float,
    modifier: Modifier = Modifier
) {
    // 현재 비율에 가장 가까운 프리셋 찾기
    val activePreset = when {
        currentRatio <= 0.35f -> 0
        currentRatio >= 0.65f -> 2
        else -> 1
    }
    
    Column(
        modifier = modifier
            .background(AppColors.GlassGrey.copy(alpha = 0.5f))
            .padding(vertical = AppDimens.PaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 상단 (시계 자리)
        Text(
            text = "12:00",
            style = AppTypography.BodyMedium
        )
        
        // 중간 (프리셋 버튼)
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.PaddingSmall)
        ) {
            repeat(3) { index ->
                val isActive = index == activePreset
                Box(
                    modifier = Modifier
                        .size(AppDimens.MinTouchTarget)
                        .clip(RoundedCornerShape(AppDimens.RadiusMedium))
                        .background(
                            if (isActive) AppColors.CarrotOrange 
                            else AppColors.WhiteAlpha10
                        )
                        .clickable { onPresetClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = AppTypography.BodyLarge,
                        color = if (isActive) AppColors.White else AppColors.SteelGrey
                    )
                }
            }
        }
        
        // 하단 (앱서랍 버튼 자리)
        Box(
            modifier = Modifier
                .size(AppDimens.MinTouchTarget)
                .clip(RoundedCornerShape(AppDimens.RadiusMedium))
                .background(AppColors.CarrotOrange),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "≡",
                style = AppTypography.Header2
            )
        }
    }
}

@Composable
fun PipArea(
    ratio: Float,
    onRatioChange: (Float) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var totalWidth by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dividerWidthPx = with(density) { AppDimens.DividerWidth.toPx() }
    
    Box(
        modifier = modifier
            .padding(AppDimens.PipPadding)
            .onGloballyPositioned { coordinates ->
                totalWidth = coordinates.size.width.toFloat()
            }
    ) {
        if (totalWidth > 0) {
            // 고정 너비 계산 (weight 대신)
            val pip1Width = with(density) { 
                ((totalWidth * ratio) - (dividerWidthPx / 2)).toDp() 
            }
            val pip2Width = with(density) { 
                ((totalWidth * (1f - ratio)) - (dividerWidthPx / 2)).toDp() 
            }
            val dividerOffset = with(density) {
                ((totalWidth * ratio) - (dividerWidthPx / 2)).toDp()
            }
            
            // PIP 1 (좌측, 고정 너비)
            PipWithGesture(
                slotIndex = 1,
                ratioText = "${(ratio * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(pip1Width.coerceAtLeast(0.dp))
            )
            
            // PIP 2 (우측, 고정 너비)
            PipWithGesture(
                slotIndex = 2,
                ratioText = "${((1f - ratio) * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(pip2Width.coerceAtLeast(0.dp))
            )
            
            // Divider (절대 위치)
            Box(
                modifier = Modifier
                    .offset(x = dividerOffset)
                    .fillMaxHeight()
                    .width(AppDimens.DividerWidth)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                isDragging = true
                                onDragStateChange(true)
                            },
                            onDragEnd = { 
                                isDragging = false
                                onDragStateChange(false)
                                // 드래그 끝나면 5% 단위로 스냅
                                val snapped = RatioPresets.snapToUnit(ratio)
                                onRatioChange(snapped.coerceIn(RatioPresets.MIN_RATIO, RatioPresets.MAX_RATIO))
                            },
                            onDragCancel = { 
                                isDragging = false 
                                onDragStateChange(false)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                // 손가락의 절대 X 위치를 비율로 변환
                                val absoluteX = change.position.x + (totalWidth * ratio) - (dividerWidthPx / 2)
                                val newRatio = (absoluteX / totalWidth).coerceIn(
                                    RatioPresets.MIN_RATIO,
                                    RatioPresets.MAX_RATIO
                                )
                                onRatioChange(newRatio)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // 손잡이
                Box(
                    modifier = Modifier
                        .width(AppDimens.DividerHandleWidth)
                        .height(AppDimens.DividerHandleHeight)
                        .clip(RoundedCornerShape(AppDimens.DividerHandleWidth / 2))
                        .background(
                            if (isDragging) AppColors.CarrotOrange 
                            else AppColors.WhiteAlpha30
                        )
                )
            }
        }
    }
}

@Composable
fun PipWithGesture(
    slotIndex: Int,
    ratioText: String = "",
    modifier: Modifier = Modifier
) {
    // AppViewSurface 참조 저장
    var appViewSurface by remember { mutableStateOf<AppViewSurface?>(null) }
    var showAppDrawer by remember { mutableStateOf(false) }
    var hasApp by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
    ) {
        // PIP 영역 - VirtualDisplay SurfaceView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PipSurfaceView(
                slotIndex = slotIndex,
                modifier = Modifier.fillMaxSize(),
                onDisplayReady = { displayId ->
                    Log.d("PipWithGesture", "Display ready: slot=$slotIndex displayId=$displayId")
                },
                onSurfaceCreated = { surface ->
                    appViewSurface = surface
                }
            )
            
            // 앱서랍 표시 (슬라이드 애니메이션)
            androidx.compose.animation.AnimatedVisibility(
                visible = showAppDrawer,
                enter = slideInVertically(
                    initialOffsetY = { it },  // 아래에서 위로
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },  // 위에서 아래로
                    animationSpec = tween(200)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.MidnightBlack.copy(alpha = 0.95f))
                        .clickable { showAppDrawer = false }
                ) {
                    AppDrawerInPip(
                        slotIndex = slotIndex,
                        onAppSelected = { packageName ->
                            appViewSurface?.launchApp(packageName)
                            hasApp = true
                            showAppDrawer = false
                            Log.d("PipWithGesture", "Launching $packageName on slot $slotIndex")
                        },
                        onDismiss = { showAppDrawer = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // 중앙 앱서랍 버튼 (앱 미실행 시에만 표시, 페이드 애니메이션)
            androidx.compose.animation.AnimatedVisibility(
                visible = !showAppDrawer && !hasApp,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(AppColors.CarrotOrange.copy(alpha = 0.8f))
                            .clickable {
                                showAppDrawer = true
                                Log.d("PipWithGesture", "App drawer button clicked on slot $slotIndex")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⊞",
                            style = AppTypography.Header1,
                            color = AppColors.White
                        )
                    }
                }
            }
        }
        
        // 제스처바 (PIP 영역 바깥, 하단)
        GestureBar(
            onSwipeUp = {
                // 위로 스와이프 → 앱서랍 표시
                showAppDrawer = true
                Log.d("GestureBar", "Swipe up - showing app drawer for slot $slotIndex")
            },
            onSwipeLeft = {
                // 왼쪽 스와이프 → 뒤로가기
                appViewSurface?.sendBackKey()
                Log.d("GestureBar", "Swipe left - Back key sent to slot $slotIndex")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.GestureBarHeight)
        )
    }
}

/**
 * PIP 영역 내부 앱서랍
 */
@Composable
fun AppDrawerInPip(
    slotIndex: Int,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<android.test.settings.ui.applist.AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 앱 목록 로드
    LaunchedEffect(Unit) {
        apps = android.test.settings.ui.applist.loadInstalledApps(context)
        isLoading = false
        Log.d("AppDrawerInPip", "Loaded ${apps.size} apps for slot $slotIndex")
    }
    
    // 이벤트 전파 차단을 위해 Box로 감싸기
    Box(
        modifier = modifier
            .clickable(enabled = false, onClick = {})  // 배경 클릭 차단
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "앱 선택",
                style = AppTypography.BodyLarge,
                color = AppColors.White
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.WhiteAlpha10)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    style = AppTypography.BodyMedium,
                    color = AppColors.White
                )
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "로딩 중...",
                    style = AppTypography.BodyMedium,
                    color = AppColors.SteelGrey
                )
            }
        } else {
            // 앱 그리드
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps.size) { index ->
                    val app = apps[index]
                    AppItemCompact(
                        app = app,
                        onClick = { 
                            Log.d("AppDrawerInPip", "App clicked: ${app.packageName}")
                            onAppSelected(app.packageName) 
                        }
                    )
                }
            }
        }
    }
    }  // Box 닫기
}  // AppDrawerInPip 닫기

@Composable
private fun AppItemCompact(
    app: android.test.settings.ui.applist.AppInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.RadiusMedium))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 앱 아이콘
        app.icon?.let { drawable ->
            androidx.compose.foundation.Image(
                bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } ?: Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.WhiteAlpha10),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.label.firstOrNull()?.toString() ?: "?",
                style = AppTypography.BodyLarge,
                color = AppColors.White
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // 앱 이름
        Text(
            text = app.label,
            style = AppTypography.Caption,
            color = AppColors.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
    }
}

/**
 * 제스처바 - 4방향 고정 애니메이션
 * 드래그 방향이 결정되면 해당 방향(좌/우/상/하)으로만 인디케이터가 움직임
 * - 좌측 스와이프: 뒤로가기
 * - 위로 스와이프: 앱서랍
 */
@Composable
fun GestureBar(
    onSwipeUp: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    
    // 스와이프 threshold (dp -> px)
    val swipeThreshold = with(density) { 30.dp.toPx() }
    val directionThreshold = with(density) { 10.dp.toPx() }  // 방향 결정 임계값
    
    var isPressed by remember { mutableStateOf(false) }
    var swiped by remember { mutableStateOf(false) }
    
    // 드래그 방향 (null = 미정, 0 = 수평, 1 = 수직)
    var dragDirection by remember { mutableStateOf<Int?>(null) }
    
    // 방향별 오프셋 (-1 ~ 1 범위로 정규화)
    var normalizedOffset by remember { mutableFloatStateOf(0f) }
    
    // 애니메이션된 오프셋 (스프링 효과)
    val animatedOffset by animateFloatAsState(
        targetValue = if (isPressed) normalizedOffset else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (isPressed) Spring.StiffnessLow else Spring.StiffnessMedium
        ),
        label = "offset"
    )
    
    // 인디케이터 크기 애니메이션
    val indicatorScale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    // 방향에 따른 X, Y 오프셋 계산
    val offsetX = when {
        dragDirection == 0 -> animatedOffset * 40f  // 수평 방향
        else -> 0f
    }
    val offsetY = when {
        dragDirection == 1 -> animatedOffset * 25f  // 수직 방향
        else -> 0f
    }
    
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                        isPressed = true
                        swiped = false
                        dragDirection = null
                        normalizedOffset = 0f
                    },
                    onDragEnd = {
                        isPressed = false
                        dragDirection = null
                        normalizedOffset = 0f
                    },
                    onDragCancel = {
                        isPressed = false
                        swiped = false
                        dragDirection = null
                        normalizedOffset = 0f
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        
                        val deltaX = change.position.x - startX
                        val deltaY = change.position.y - startY
                        val absX = kotlin.math.abs(deltaX)
                        val absY = kotlin.math.abs(deltaY)
                        
                        // 방향이 아직 결정되지 않았으면 결정
                        if (dragDirection == null && (absX > directionThreshold || absY > directionThreshold)) {
                            dragDirection = if (absX > absY) 0 else 1  // 0=수평, 1=수직
                            Log.d("GestureBar", "Direction locked: ${if (dragDirection == 0) "HORIZONTAL" else "VERTICAL"}")
                        }
                        
                        // 방향에 따라 오프셋 업데이트 (-1 ~ 1 범위)
                        when (dragDirection) {
                            0 -> normalizedOffset = (deltaX / 80f).coerceIn(-1f, 1f)  // 수평
                            1 -> normalizedOffset = (deltaY / 50f).coerceIn(-1f, 1f)  // 수직
                        }
                        
                        if (swiped) return@detectDragGestures
                        
                        // 스와이프 액션 트리거
                        when (dragDirection) {
                            0 -> {  // 수평 방향
                                if (deltaX < -swipeThreshold) {
                                    swiped = true
                                    Log.d("GestureBar", "LEFT swipe -> Back")
                                    onSwipeLeft()
                                } else if (deltaX > swipeThreshold) {
                                    swiped = true
                                    Log.d("GestureBar", "RIGHT swipe")
                                }
                            }
                            1 -> {  // 수직 방향
                                if (deltaY < -swipeThreshold) {
                                    swiped = true
                                    Log.d("GestureBar", "UP swipe -> AppDrawer")
                                    onSwipeUp()
                                } else if (deltaY > swipeThreshold) {
                                    swiped = true
                                    Log.d("GestureBar", "DOWN swipe")
                                }
                            }
                        }
                    }
                )
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                
                // 시스템 제스처 제외 영역 - 상하좌우 여유 추가
                val padding = 20
                val exclusionRect = Rect(
                    (position.x - padding).toInt().coerceAtLeast(0),
                    (position.y - padding).toInt().coerceAtLeast(0),
                    (position.x + size.width + padding).toInt(),
                    (position.y + size.height + padding).toInt()
                )
                
                // 기존 제스처 제외 영역을 완전히 교체
                view.systemGestureExclusionRects = listOf(exclusionRect)
            },
        contentAlignment = Alignment.Center
    ) {
        // 인디케이터 바 (방향이 고정된 후 해당 방향으로만 움직임)
        Box(
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .width(AppDimens.GestureBarIndicatorWidth * indicatorScale)
                .height(AppDimens.GestureBarIndicatorHeight)
                .clip(RoundedCornerShape(AppDimens.GestureBarIndicatorHeight / 2))
                .background(
                    if (isPressed) AppColors.CarrotOrange
                    else AppColors.WhiteAlpha30
                )
        )
    }
}
