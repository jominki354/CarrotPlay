package android.test.settings.ui.theme

import androidx.compose.ui.unit.dp

/**
 * CarrotPlay 치수 상수
 * 
 * 타겟 디스플레이: 현대/기아 5W (10.25인치, 1920x720, ~160dpi)
 */
object AppDimens {
    // 코너 라운드
    val RadiusLarge = 18.dp
    val RadiusMedium = 12.dp
    val RadiusSmall = 8.dp
    
    // 패딩
    val PaddingLarge = 24.dp
    val PaddingMedium = 16.dp
    val PaddingSmall = 8.dp
    val PaddingTiny = 4.dp
    
    // 레이아웃 상수
    val DockWidth = 72.dp
    val MinTouchTarget = 44.dp
    val DividerWidth = 16.dp              // 좌우 여백 축소 (24 → 16)
    val DividerHandleWidth = 4.dp
    val DividerHandleHeight = 64.dp       // 길이 150% 증가 (40 → 64)
    val GestureBarHeight = 28.dp          // 시스템 제스처 영역 확보를 위해 증가
    val GestureBarIndicatorWidth = 64.dp  // 길이 150% 증가 (40 → 64)
    val GestureBarIndicatorHeight = 4.dp
    val PipBorder = 2.dp
    val PipPadding = 8.dp
    
    // 아이콘 크기
    val IconSmall = 24.dp
    val IconMedium = 32.dp
    val IconLarge = 48.dp
    
    // 앱 아이콘
    val AppIconSize = 56.dp
    val AppIconSizeSmall = 40.dp
}
