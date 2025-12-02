package android.test.settings.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * CarrotPlay 타이포그래피
 */
object AppTypography {
    val Header1 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.White
    )
    
    val Header2 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.White
    )
    
    val Header3 = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.White
    )
    
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = AppColors.White
    )
    
    val BodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = AppColors.White
    )
    
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = AppColors.SteelGrey
    )
    
    val Button = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.White
    )
}
