package android.test.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManagerGlobal;

import java.lang.reflect.Method;

/**
 * AppViewSurface - VirtualDisplay 기반 앱 표시 및 터치 주입
 * 
 * 리버스 엔지니어링된 z7/g.java를 기반으로 구현.
 * Hidden API 접근은 리플렉션을 통해 수행.
 * 
 * 핵심 변경 (원본 앱 방식):
 * - VirtualDisplayManager를 통해 VirtualDisplay 캐시 관리
 * - PlatformView가 재생성되어도 기존 VirtualDisplay 재사용
 * - dispatchTouchToVirtualDisplay(): 외부에서 호출 가능한 터치 주입 메서드
 * - 원본 MotionEvent의 deviceId, downTime, source 등 속성을 유지
 * - displayId만 변경하여 InputManager.injectInputEvent() 호출
 */
public class AppViewSurface extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "AppViewSurface";
    
    // 터치 오프셋 (원본 앱 z7/g.java와 동일)
    public float touchOffsetX = 0.0f;
    public float touchOffsetY = 0.0f;
    
    // Reflection 캐시
    private static Method sSetDisplayIdMotion;
    private static Method sSetDisplayIdKey;
    private static boolean sReflectionInitialized = false;
    
    // VirtualDisplayManager 사용 (싱글톤 캐시)
    private VirtualDisplayManager vdManager;
    private int viewId = -1;  // PlatformView viewId (VirtualDisplayManager 캐시 키)
    
    private int displayId = -1;
    private String targetPackage;
    private boolean surfaceReady = false;
    private boolean displayCreated = false;
    private int displayWidth = 800;
    private int displayHeight = 480;
    private int displayDensity = 160;
    private boolean isTouchEnabled = true;
    
    private Handler mainHandler;
    
    public AppViewSurface(Context context) {
        super(context);
        init(context);
    }
    
    public AppViewSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public AppViewSurface(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    /**
     * VirtualDisplayPlatformView에서 사용하는 생성자
     * viewId를 전달받아 VirtualDisplayManager 캐시 키로 사용
     */
    public AppViewSurface(Context context, int viewId, int width, int height, int density) {
        super(context);
        this.viewId = viewId;
        this.displayWidth = width;
        this.displayHeight = height;
        this.displayDensity = density;
        init(context);
    }
    
    private void init(Context context) {
        initReflection();
        vdManager = VirtualDisplayManager.getInstance(context);
        mainHandler = new Handler(Looper.getMainLooper());
        getHolder().addCallback(this);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        
        Log.d(TAG, "AppViewSurface init: viewId=" + viewId);
    }
    
    /**
     * 리플렉션 메서드 초기화 (한 번만)
     */
    @SuppressLint("PrivateApi")
    private static synchronized void initReflection() {
        if (sReflectionInitialized) return;
        
        try {
            // MotionEvent.setDisplayId(int)
            sSetDisplayIdMotion = MotionEvent.class.getDeclaredMethod("setDisplayId", int.class);
            sSetDisplayIdMotion.setAccessible(true);
            Log.d(TAG, "Reflection: MotionEvent.setDisplayId initialized");
        } catch (Exception e) {
            Log.e(TAG, "Reflection: MotionEvent.setDisplayId not found", e);
        }
        
        try {
            // KeyEvent.setDisplayId(int)
            sSetDisplayIdKey = KeyEvent.class.getDeclaredMethod("setDisplayId", int.class);
            sSetDisplayIdKey.setAccessible(true);
            Log.d(TAG, "Reflection: KeyEvent.setDisplayId initialized");
        } catch (Exception e) {
            Log.e(TAG, "Reflection: KeyEvent.setDisplayId not found", e);
        }
        
        sReflectionInitialized = true;
    }
    
    /**
     * MotionEvent.setDisplayId() 리플렉션 호출
     */
    private void setMotionEventDisplayId(MotionEvent event, int displayId) {
        if (sSetDisplayIdMotion == null) {
            Log.w(TAG, "MotionEvent.setDisplayId method not available");
            return;
        }
        
        try {
            sSetDisplayIdMotion.invoke(event, displayId);
        } catch (Exception e) {
            Log.e(TAG, "MotionEvent.setDisplayId failed", e);
        }
    }
    
    /**
     * KeyEvent.setDisplayId() 리플렉션 호출
     */
    private void setKeyEventDisplayId(KeyEvent event, int displayId) {
        if (sSetDisplayIdKey == null) {
            Log.w(TAG, "KeyEvent.setDisplayId method not available");
            return;
        }
        
        try {
            sSetDisplayIdKey.invoke(event, displayId);
        } catch (Exception e) {
            Log.e(TAG, "KeyEvent.setDisplayId failed", e);
        }
    }
    
    /**
     * viewId 설정 (VirtualDisplayManager 캐시 키)
     */
    public void setViewId(int viewId) {
        this.viewId = viewId;
        Log.d(TAG, "viewId set to: " + viewId);
    }
    
    public int getViewId() {
        return viewId;
    }
    
    public void setTargetPackage(String packageName) {
        this.targetPackage = packageName;
    }
    
    public void setDisplaySize(int width, int height, int density) {
        this.displayWidth = width;
        this.displayHeight = height;
        this.displayDensity = density;
        
        // VirtualDisplayManager를 통해 크기 조정
        if (viewId >= 0) {
            VirtualDisplayManager.VirtualDisplayInfo info = vdManager.getDisplayInfo(viewId);
            if (info != null && info.virtualDisplay != null) {
                info.virtualDisplay.resize(width, height, density);
                info.width = width;
                info.height = height;
                info.density = density;
            }
        }
    }
    
    public int getDisplayId() {
        return displayId;
    }
    
    public int getDisplayWidth() {
        return displayWidth;
    }
    
    public int getDisplayHeight() {
        return displayHeight;
    }
    
    public int getDisplayDensity() {
        return displayDensity;
    }
    
    public boolean isTouchEnabled() {
        return isTouchEnabled;
    }
    
    public void setTouchEnabled(boolean enabled) {
        this.isTouchEnabled = enabled;
    }
    
    public boolean isDisplayCreated() {
        return displayCreated;
    }
    
    /**
     * surfaceCreated - 원본 z7/f.java: 아무것도 하지 않음
     * VirtualDisplay 생성은 surfaceChanged()에서 처리
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated viewId=" + viewId + " (no-op, waiting for surfaceChanged)");
        surfaceReady = true;
    }
    
    /**
     * surfaceChanged - VirtualDisplayManager를 통해 캐시된 VirtualDisplay 사용
     * PlatformView가 재생성되어도 기존 VirtualDisplay 재사용
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged: viewId=" + viewId + " " + width + "x" + height + " format=" + format);
        
        if (viewId < 0) {
            Log.e(TAG, "surfaceChanged: viewId not set!");
            return;
        }
        
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            Log.e(TAG, "surfaceChanged: surface is invalid");
            return;
        }
        
        // VirtualDisplayManager를 통해 캐시된 VirtualDisplay 사용 또는 새로 생성
        VirtualDisplayManager.VirtualDisplayInfo info = vdManager.getOrCreateDisplay(
            viewId, surface, width, height, displayDensity);
        
        if (info != null) {
            displayId = info.displayId;
            displayCreated = true;
            displayWidth = info.width;
            displayHeight = info.height;
            Log.i(TAG, "VirtualDisplay ready: viewId=" + viewId + " displayId=" + displayId + 
                  " size=" + displayWidth + "x" + displayHeight);
        } else {
            Log.e(TAG, "Failed to get/create VirtualDisplay");
        }
    }
    
    /**
     * surfaceDestroyed - Surface만 분리, VirtualDisplay는 캐시에 유지
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed viewId=" + viewId);
        surfaceReady = false;
        
        // Surface만 분리 (VirtualDisplay는 캐시에 유지)
        if (viewId >= 0) {
            vdManager.detachSurface(viewId);
        }
        // displayId와 displayCreated는 유지 (캐시에서 재사용 가능)
    }
    
    /**
     * VirtualDisplay 생성 (직접 호출용 - 기존 호환성)
     * 일반적으로는 surfaceChanged()에서 자동 생성됨
     */
    public void createVirtualDisplay() {
        if (viewId < 0) {
            Log.w(TAG, "createVirtualDisplay: viewId not set");
            return;
        }
        
        if (displayCreated) {
            Log.w(TAG, "VirtualDisplay already created");
            return;
        }
        
        Surface surface = surfaceReady ? getHolder().getSurface() : null;
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "createVirtualDisplay: surface not ready, deferring to surfaceChanged");
            return;
        }
        
        VirtualDisplayManager.VirtualDisplayInfo info = vdManager.getOrCreateDisplay(
            viewId, surface, displayWidth, displayHeight, displayDensity);
        
        if (info != null) {
            displayId = info.displayId;
            displayCreated = true;
            Log.i(TAG, "VirtualDisplay created: viewId=" + viewId + " displayId=" + displayId);
        }
    }
    
    /**
     * VirtualDisplay 해제 (완전 해제 - 캐시에서도 제거)
     */
    public void releaseVirtualDisplay() {
        if (viewId >= 0) {
            vdManager.releaseDisplay(viewId);
        }
        displayId = -1;
        displayCreated = false;
        Log.d(TAG, "VirtualDisplay released: viewId=" + viewId);
    }
    
    /**
     * 앱 실행 on VirtualDisplay
     */
    public boolean launchApp(String packageName) {
        if (!displayCreated || displayId < 0) {
            Log.e(TAG, "Cannot launch app: display not ready");
            return false;
        }
        
        Context context = getContext();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        
        if (launchIntent == null) {
            Log.e(TAG, "Cannot find launch intent for: " + packageName);
            return false;
        }
        
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        
        try {
            // ActivityOptions를 통해 특정 display에서 실행
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            context.startActivity(launchIntent, options.toBundle());
            targetPackage = packageName;
            Log.d(TAG, "Launched " + packageName + " on display " + displayId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app", e);
            return false;
        }
    }
    
    /**
     * 터치 이벤트 주입 (z7/g.java dispatchTouchEvent 패턴)
     * PlatformView 모드에서는 이 메서드가 아닌 dispatchTouchToVirtualDisplay()를 사용
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!displayCreated || displayId < 0) {
            return super.onTouchEvent(event);
        }
        
        if (!isTouchEnabled) {
            return false;
        }
        
        // PlatformView에서 호출될 때는 이미 처리되었으므로 무시
        // Native SurfaceView 터치 이벤트 처리
        injectTouchEvent(event);
        return true;
    }
    
    /**
     * 외부에서 호출 가능한 터치 주입 메서드 (원본 z7/g.java의 b(MotionEvent) 메서드)
     * PlatformViewTouchHandler에서 호출
     * 
     * 성능 최적화:
     * - MOVE 이벤트에서 syncInputTransactions 호출 빈도 감소 (매 5번마다)
     * - MOVE에서 sync 완전 제거 (가장 큰 성능 개선)
     * - UP/CANCEL 시에만 sync
     * - 로그 완전 제거
     * 
     * 핵심: 원본 MotionEvent를 그대로 사용하고, displayId만 변경
     */
    @SuppressLint("PrivateApi")
    public void dispatchTouchToVirtualDisplay(MotionEvent motionEvent) {
        if (!displayCreated || displayId < 0) {
            return;
        }
        
        // displayId 설정
        setMotionEventDisplayId(motionEvent, displayId);
        
        int actionMasked = motionEvent.getActionMasked();
        
        // 성능 최적화 v2: MOVE에서 sync 완전 제거!
        // UP/CANCEL 시에만 sync (터치 완료 정확성 유지)
        boolean syncAfter = actionMasked == MotionEvent.ACTION_UP 
            || actionMasked == MotionEvent.ACTION_POINTER_UP 
            || actionMasked == MotionEvent.ACTION_CANCEL;
        
        try {
            // 이벤트 주입 (완전 비동기)
            InputManager inputManager = InputManager.getInstance();
            inputManager.injectInputEvent(motionEvent, 0);
            
            // UP/CANCEL 후에만 sync
            if (syncAfter) {
                WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "dispatchTouchToVirtualDisplay failed", e);
        }
    }
    
    private String getActionName(int actionMasked) {
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: return "DOWN";
            case MotionEvent.ACTION_UP: return "UP";
            case MotionEvent.ACTION_MOVE: return "MOVE";
            case MotionEvent.ACTION_CANCEL: return "CANCEL";
            case MotionEvent.ACTION_POINTER_DOWN: return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP: return "POINTER_UP";
            default: return "ACTION_" + actionMasked;
        }
    }
    
    /**
     * 터치 오프셋 getter (원본 앱 z7/g.java와 동일)
     * PlatformViewTouchHandler에서 좌표 계산 시 사용
     */
    public float getTouchOffsetX() {
        return touchOffsetX;
    }
    
    public float getTouchOffsetY() {
        return touchOffsetY;
    }
    
    /**
     * 터치 오프셋 setter
     */
    public void setTouchOffset(float offsetX, float offsetY) {
        this.touchOffsetX = offsetX;
        this.touchOffsetY = offsetY;
    }
    
    /**
     * 터치 이벤트 주입 (내부용 - SurfaceView 터치 이벤트 처리)
     * 
     * 성능 최적화 v2:
     * - MOVE 이벤트에서 syncInputTransactions 완전 제거 (가장 큰 병목)
     * - UP/CANCEL 이벤트 후에만 sync 호출
     * - 로그 완전 제거
     * 
     * 핵심: 원본 MotionEvent를 최대한 유지하고, displayId만 변경
     */
    @SuppressLint("PrivateApi")
    private void injectTouchEvent(MotionEvent event) {
        // 좌표 스케일링 계산
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        
        if (viewWidth <= 0 || viewHeight <= 0) return;
        
        float scaleX = (float) displayWidth / viewWidth;
        float scaleY = (float) displayHeight / viewHeight;
        
        // 새 이벤트 생성 (스케일된 좌표)
        MotionEvent scaledEvent = MotionEvent.obtain(event);
        scaledEvent.setLocation(event.getX() * scaleX, event.getY() * scaleY);
        
        // displayId 설정 (리플렉션)
        setMotionEventDisplayId(scaledEvent, displayId);
        
        int actionMasked = scaledEvent.getActionMasked();
        
        // 성능 최적화 v2: UP/CANCEL 후에만 sync (MOVE에서 sync 완전 제거)
        boolean syncAfter = actionMasked == MotionEvent.ACTION_UP 
            || actionMasked == MotionEvent.ACTION_POINTER_UP 
            || actionMasked == MotionEvent.ACTION_CANCEL 
            || actionMasked == MotionEvent.ACTION_HOVER_EXIT;
        
        try {
            // InputManager를 통한 주입 (INJECT_INPUT_EVENT_MODE_ASYNC = 0)
            InputManager inputManager = InputManager.getInstance();
            inputManager.injectInputEvent(scaledEvent, 0);
            
            // 성능 최적화 v2: UP/CANCEL 후에만 동기화
            if (syncAfter) {
                WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true);
            }
        } catch (Exception e) {
            // 에러 로그만 유지
        } finally {
            scaledEvent.recycle();
        }
    }
    
    /**
     * Display 상태 제어 (숨김) - VirtualDisplayManager 사용
     */
    public void hideDisplay() {
        if (viewId >= 0) {
            vdManager.detachSurface(viewId);
        }
    }
    
    /**
     * Display 상태 제어 (표시) - Surface 재연결
     */
    public void showDisplay() {
        if (viewId >= 0 && surfaceReady) {
            Surface surface = getHolder().getSurface();
            if (surface != null && surface.isValid()) {
                vdManager.attachSurface(viewId, surface, displayWidth, displayHeight);
            }
        }
    }
    
    /**
     * 뒤로가기 키 전송
     */
    public void sendBackKey() {
        if (!displayCreated || displayId < 0) {
            Log.w(TAG, "Cannot send back key: display not ready");
            return;
        }
        
        long now = SystemClock.uptimeMillis();
        
        KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
        setKeyEventDisplayId(downEvent, displayId);
        
        KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0);
        setKeyEventDisplayId(upEvent, displayId);
        
        try {
            InputManager inputManager = InputManager.getInstance();
            inputManager.injectInputEvent(downEvent, 0);
            inputManager.injectInputEvent(upEvent, 0);
            Log.d(TAG, "Back key sent to display " + displayId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send back key", e);
        }
    }
    
    /**
     * 홈 키 전송
     */
    public void sendHomeKey() {
        if (!displayCreated || displayId < 0) {
            Log.w(TAG, "Cannot send home key: display not ready");
            return;
        }
        
        long now = SystemClock.uptimeMillis();
        
        KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 0);
        setKeyEventDisplayId(downEvent, displayId);
        
        KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 0);
        setKeyEventDisplayId(upEvent, displayId);
        
        try {
            InputManager inputManager = InputManager.getInstance();
            inputManager.injectInputEvent(downEvent, 0);
            inputManager.injectInputEvent(upEvent, 0);
            Log.d(TAG, "Home key sent to display " + displayId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send home key", e);
        }
    }
    
    /**
     * 정리 (release alias for compatibility)
     * 주의: 일반적으로는 Surface만 분리하고 VirtualDisplay는 캐시에 유지
     * 완전 해제가 필요한 경우에만 호출
     */
    public void release() {
        // VirtualDisplay는 캐시에 유지 (재사용 가능)
        // 완전 해제가 필요하면 releaseVirtualDisplay() 호출
        Log.d(TAG, "release() called - detaching surface only (VirtualDisplay cached)");
        if (viewId >= 0) {
            vdManager.detachSurface(viewId);
        }
    }
    
    /**
     * 정리 - PlatformView dispose 시 호출
     * VirtualDisplay는 캐시에 유지하여 PlatformView 재생성 시 재사용
     */
    public void dispose() {
        Log.d(TAG, "dispose() called - detaching surface only (VirtualDisplay cached)");
        if (viewId >= 0) {
            vdManager.detachSurface(viewId);
        }
        // displayId, displayCreated는 유지 (캐시에서 재사용 가능)
    }
    
    /**
     * 완전 정리 - VirtualDisplay 캐시에서도 제거
     * 앱 종료 또는 슬롯 완전 제거 시에만 호출
     */
    public void fullRelease() {
        Log.d(TAG, "fullRelease() called - releasing VirtualDisplay from cache");
        releaseVirtualDisplay();
    }
}
