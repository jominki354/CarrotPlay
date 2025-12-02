package android.test.settings.ui.applist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import android.test.settings.ui.theme.AppColors
import android.test.settings.ui.theme.AppDimens
import android.test.settings.ui.theme.AppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 앱 정보 데이터 클래스
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * 설치된 앱 목록을 로드
 */
suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }
    
    pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            // 자기 자신 제외
            if (appInfo.packageName == context.packageName) return@mapNotNull null
            
            AppInfo(
                packageName = appInfo.packageName,
                label = appInfo.loadLabel(pm).toString(),
                icon = appInfo.loadIcon(pm)
            )
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * 앱 선택 다이얼로그
 */
@Composable
fun AppListDialog(
    slotIndex: Int,
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 앱 목록 로드
    LaunchedEffect(Unit) {
        apps = loadInstalledApps(context)
        isLoading = false
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .clip(RoundedCornerShape(AppDimens.RadiusLarge))
                .background(AppColors.GlassGrey.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 헤더
                Text(
                    text = "PIP $slotIndex - 앱 선택",
                    style = AppTypography.Header2,
                    color = AppColors.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(apps) { app ->
                            AppItem(
                                app = app,
                                onClick = {
                                    onAppSelected(app.packageName)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
            
            // 닫기 버튼
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.WhiteAlpha10)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    style = AppTypography.BodyLarge,
                    color = AppColors.White
                )
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.RadiusMedium))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 앱 아이콘
        app.icon?.let { drawable ->
            Image(
                bitmap = drawable.toBitmap(64, 64).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } ?: Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.WhiteAlpha10),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.label.firstOrNull()?.toString() ?: "?",
                style = AppTypography.Header2,
                color = AppColors.White
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 앱 이름
        Text(
            text = app.label,
            style = AppTypography.Caption,
            color = AppColors.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
    }
}
