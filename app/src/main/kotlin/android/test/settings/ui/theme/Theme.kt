package android.test.settings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.CarrotOrange,
    secondary = AppColors.InfoBlue,
    tertiary = AppColors.SuccessGreen,
    background = AppColors.MidnightBlack,
    surface = AppColors.GlassGrey,
    onPrimary = AppColors.White,
    onSecondary = AppColors.White,
    onTertiary = AppColors.White,
    onBackground = AppColors.White,
    onSurface = AppColors.White,
    error = AppColors.ErrorRed,
    onError = AppColors.White
)

@Composable
fun CarrotPlayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
