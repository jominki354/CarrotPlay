package android.test.settings

import android.app.Activity
import android.os.Bundle

/**
 * 투명한 빈 Activity - 다른 앱을 백그라운드로 보내기 위해 사용
 * 이 Activity가 시작되면 이전 앱이 백그라운드로 밀리고,
 * 즉시 finish()되어 빈 화면만 남김
 */
class TransparentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 즉시 종료 - 이전 앱을 백그라운드로 밀기만 하고 사라짐
        finish()
    }
}
