package com.example.carcar_launcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * TaskManager - 원본 CarCarLauncher의 z7/m.java + z7/l.java 구현
 * 
 * 시스템 권한이 필요합니다:
 * - android:sharedUserId="android.uid.system"
 * - AOSP 플랫폼 키로 서명
 * 
 * Hidden API는 리플렉션으로 접근합니다.
 */
class TaskManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TaskManager"
    }
    
    // 시스템 서비스 (리플렉션으로 획득)
    private var activityManager: Any? = null      // IActivityManager
    private var activityTaskManager: Any? = null  // IActivityTaskManager
    private val launcherApps: LauncherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    
    // Task 리스너
    private var taskStackListener: Any? = null
    private var isListenerRegistered = false
    
    // 상태 추적
    private val displayPackageMap = HashMap<Int, String>()
    private val pendingLaunches = HashMap<Int, String>()
    
    // 콜백
    var onAppChanged: ((displayId: Int, packageName: String) -> Unit)? = null
    var onAppClosed: ((displayId: Int) -> Unit)? = null
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    init {
        initSystemServices()
    }
    
    /**
     * 리플렉션으로 시스템 서비스 획득
     */
    private fun initSystemServices() {
        try {
            // ActivityManager.getService() -> IActivityManager
            val amClass = Class.forName("android.app.ActivityManager")
            val getServiceMethod = amClass.getDeclaredMethod("getService")
            activityManager = getServiceMethod.invoke(null)
            Log.d(TAG, "IActivityManager obtained: $activityManager")
            
            // ActivityTaskManager.getService() -> IActivityTaskManager
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val atmGetServiceMethod = atmClass.getDeclaredMethod("getService")
            activityTaskManager = atmGetServiceMethod.invoke(null)
            Log.d(TAG, "IActivityTaskManager obtained: $activityTaskManager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get system services via reflection", e)
        }
    }
    
    /**
     * TaskStackListener 등록 (리플렉션 사용)
     * 
     * 주의: TaskStackListener는 Hidden API이므로 런타임에 동적 생성 필요
     * 실패 시 Root 기반 폴링으로 fallback
     */
    fun startListening(): Boolean {
        if (isListenerRegistered || activityManager == null) return false
        
        try {
            // TaskStackListener 클래스 로드
            val listenerClass = Class.forName("android.app.TaskStackListener")
            
            // 동적 서브클래스 생성은 어려우므로 직접 콜백 등록 시도
            // 실제로는 Hidden API stub JAR 없이는 컴파일 타임에 상속 불가
            
            // Alternative: Runtime에 Proxy로 ITaskStackListener 구현
            val iListenerClass = Class.forName("android.app.ITaskStackListener")
            
            // Proxy 생성
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                iListenerClass.classLoader,
                arrayOf(iListenerClass)
            ) { _, method, args ->
                handleListenerCallback(method.name, args)
            }
            
            taskStackListener = proxy
            
            // IActivityManager.registerTaskStackListener(listener)
            val registerMethod = activityManager!!.javaClass.getDeclaredMethod(
                "registerTaskStackListener",
                iListenerClass
            )
            registerMethod.invoke(activityManager, taskStackListener)
            
            isListenerRegistered = true
            Log.i(TAG, "TaskStackListener registered via Proxy")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register TaskStackListener: ${e.message}")
            Log.w(TAG, "System API not available. Use Root-based task monitoring instead.")
            return false
        }
    }
    
    /**
     * Proxy 콜백 핸들러
     */
    private fun handleListenerCallback(methodName: String, args: Array<Any>?): Any? {
        try {
            when (methodName) {
                "onTaskCreated" -> {
                    val taskId = args?.getOrNull(0) as? Int ?: return null
                    val componentName = args?.getOrNull(1) as? ComponentName
                    if (componentName != null) {
                        handleTaskCreated(taskId, componentName)
                    }
                }
                "onTaskMovedToFront" -> {
                    val taskInfo = args?.getOrNull(0) ?: return null
                    handleTaskMovedToFront(taskInfo)
                }
                "onTaskDescriptionChanged" -> {
                    val taskInfo = args?.getOrNull(0) ?: return null
                    handleTaskDescriptionChanged(taskInfo)
                }
                "onTaskMovedToBack" -> {
                    val taskInfo = args?.getOrNull(0) ?: return null
                    handleTaskMovedToBack(taskInfo)
                }
                "onTaskRemovalStarted" -> {
                    val taskInfo = args?.getOrNull(0) ?: return null
                    handleTaskRemovalStarted(taskInfo)
                }
                "asBinder" -> {
                    // IBinder 반환 - Stub에서 처리
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listener callback: $methodName", e)
        }
        return null
    }
    
    // ====================================
    // Task 이벤트 핸들러
    // ====================================
    
    private fun handleTaskCreated(taskId: Int, componentName: ComponentName) {
        Log.d(TAG, "onTaskCreated: taskId=$taskId, component=${componentName.flattenToShortString()}")
        
        // setTaskResizeable 호출
        try {
            val setResizeableMethod = activityTaskManager?.javaClass?.getDeclaredMethod(
                "setTaskResizeable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setResizeableMethod?.invoke(activityTaskManager, taskId, 4)
            Log.d(TAG, "Set task $taskId resizeable")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set task resizeable", e)
        }
        
        // Task 정보 조회 후 콜백
        val taskInfo = findTaskById(taskId)
        if (taskInfo != null) {
            val displayId = getTaskDisplayId(taskInfo)
            mainHandler.post {
                handleTaskChanged(taskId, componentName, displayId)
            }
        }
    }
    
    private fun handleTaskMovedToFront(taskInfo: Any) {
        val componentName = getTaskComponent(taskInfo) ?: return
        val taskId = getTaskId(taskInfo)
        val displayId = getTaskDisplayId(taskInfo)
        
        Log.d(TAG, "onTaskMovedToFront: taskId=$taskId, display=$displayId")
        
        mainHandler.post {
            handleTaskChanged(taskId, componentName, displayId)
            
            // pending launch 처리
            val pendingPackage = pendingLaunches.remove(displayId)
            if (pendingPackage != null && pendingPackage == componentName.packageName) {
                setFocusedRootTask(taskId)
            }
        }
    }
    
    private fun handleTaskDescriptionChanged(taskInfo: Any) {
        val isVisible = getTaskVisibility(taskInfo)
        if (!isVisible) return
        
        val componentName = getTaskComponent(taskInfo) ?: return
        val taskId = getTaskId(taskInfo)
        val displayId = getTaskDisplayId(taskInfo)
        
        Log.d(TAG, "onTaskDescriptionChanged: taskId=$taskId")
        
        mainHandler.post {
            handleTaskChanged(taskId, componentName, displayId)
        }
    }
    
    private fun handleTaskMovedToBack(taskInfo: Any) {
        val displayId = getTaskDisplayId(taskInfo)
        val taskId = getTaskId(taskInfo)
        
        Log.d(TAG, "onTaskMovedToBack: taskId=$taskId, display=$displayId")
        
        mainHandler.post {
            val visibleTask = findVisibleTaskOnDisplay(displayId, taskId)
            if (visibleTask != null) {
                val component = getTaskComponent(visibleTask)
                if (component != null) {
                    handleTaskChanged(getTaskId(visibleTask), component, displayId)
                }
            }
        }
    }
    
    private fun handleTaskRemovalStarted(taskInfo: Any) {
        val displayId = getTaskDisplayId(taskInfo)
        val taskId = getTaskId(taskInfo)
        
        Log.d(TAG, "onTaskRemovalStarted: taskId=$taskId, display=$displayId")
        
        mainHandler.post {
            val otherTask = findVisibleTaskOnDisplay(displayId, taskId)
            if (otherTask == null) {
                displayPackageMap.remove(displayId)
                onAppClosed?.invoke(displayId)
            }
        }
    }
    
    private fun handleTaskChanged(taskId: Int, componentName: ComponentName, displayId: Int) {
        val packageName = componentName.packageName
        
        if (displayPackageMap[displayId] == packageName) {
            return
        }
        
        Log.d(TAG, "Task changed: $packageName on display $displayId")
        
        removePackageFromDisplays(packageName)
        displayPackageMap[displayId] = packageName
        onAppChanged?.invoke(displayId, packageName)
    }
    
    // ====================================
    // 리플렉션 헬퍼 메서드
    // ====================================
    
    private fun getTaskComponent(taskInfo: Any): ComponentName? {
        return try {
            val clazz = taskInfo.javaClass
            var component: ComponentName? = null
            
            for (fieldName in listOf("topActivity", "baseActivity", "origActivity", "realActivity")) {
                try {
                    component = clazz.getField(fieldName).get(taskInfo) as? ComponentName
                    if (component != null) break
                } catch (e: NoSuchFieldException) {
                    // continue to next field
                }
            }
            component
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get task component", e)
            null
        }
    }
    
    private fun getTaskId(taskInfo: Any): Int {
        return try {
            taskInfo.javaClass.getField("taskId").get(taskInfo) as? Int ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getTaskDisplayId(taskInfo: Any): Int {
        return try {
            taskInfo.javaClass.getField("displayId").get(taskInfo) as? Int ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getTaskVisibility(taskInfo: Any): Boolean {
        return try {
            val field = try {
                taskInfo.javaClass.getField("isVisible")
            } catch (e: NoSuchFieldException) {
                taskInfo.javaClass.getField("visible")
            }
            field.get(taskInfo) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun setFocusedRootTask(taskId: Int) {
        try {
            val method = activityTaskManager?.javaClass?.getDeclaredMethod(
                "setFocusedRootTask",
                Int::class.javaPrimitiveType
            )
            method?.invoke(activityTaskManager, taskId)
            Log.d(TAG, "Set focused root task: $taskId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set focused task", e)
        }
    }
    
    // ====================================
    // Task 조회 메서드
    // ====================================
    
    private fun getAllRootTaskInfos(): List<Any>? {
        return try {
            val method = activityManager?.javaClass?.getDeclaredMethod("getAllRootTaskInfos")
            @Suppress("UNCHECKED_CAST")
            method?.invoke(activityManager) as? List<Any>
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all root task infos", e)
            null
        }
    }
    
    fun findTaskById(taskId: Int): Any? {
        val allTasks = getAllRootTaskInfos() ?: return null
        
        for (taskInfo in allTasks) {
            if (getTaskId(taskInfo) == taskId) {
                return taskInfo
            }
            try {
                val childIds = taskInfo.javaClass.getField("childTaskIds").get(taskInfo) as? IntArray
                if (childIds != null && taskId in childIds) {
                    return taskInfo
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }
    
    fun findTaskByPackage(packageName: String): Any? {
        val allTasks = getAllRootTaskInfos() ?: return null
        
        for (taskInfo in allTasks) {
            val component = getTaskComponent(taskInfo)
            if (component?.packageName == packageName) {
                return taskInfo
            }
        }
        return null
    }
    
    private fun findVisibleTaskOnDisplay(displayId: Int, excludeTaskId: Int = -1): Any? {
        val allTasks = getAllRootTaskInfos() ?: return null
        
        for (taskInfo in allTasks) {
            val taskDisplayId = getTaskDisplayId(taskInfo)
            val taskId = getTaskId(taskInfo)
            val visible = getTaskVisibility(taskInfo)
            
            if (taskDisplayId == displayId && visible && taskId != excludeTaskId) {
                return taskInfo
            }
        }
        return null
    }
    
    // ====================================
    // 공개 API
    // ====================================
    
    fun launchAppOnDisplay(packageName: String, displayId: Int): Boolean {
        Log.i(TAG, "launchAppOnDisplay: $packageName on display $displayId")
        
        if (displayId == -1) {
            Log.e(TAG, "Invalid display ID")
            return false
        }
        
        // 이미 해당 디스플레이에서 실행 중인지 확인
        val existingTask = findTaskByPackage(packageName)
        if (existingTask != null) {
            val taskDisplayId = getTaskDisplayId(existingTask)
            if (taskDisplayId == displayId) {
                val visible = getTaskVisibility(existingTask)
                if (!visible) {
                    val taskId = getTaskId(existingTask)
                    setFocusedRootTask(taskId)
                    Log.d(TAG, "Set focus to existing task $taskId")
                }
                return true
            }
        }
        
        // 메인 디스플레이가 아니면 pending에 추가
        if (displayId != context.display?.displayId) {
            pendingLaunches[displayId] = packageName
        }
        
        // Launcher Activity 찾기
        val activityList = launcherApps.getActivityList(packageName, Process.myUserHandle())
        if (activityList.isNullOrEmpty()) {
            Log.e(TAG, "No launcher activity found for $packageName")
            return false
        }
        
        val componentName = activityList[0].componentName
        
        // Intent 생성
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = componentName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }
        
        // ActivityOptions 설정
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        
        try {
            val setWindowingModeMethod = ActivityOptions::class.java.getDeclaredMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            setWindowingModeMethod.invoke(options, 1)
            Log.d(TAG, "setLaunchWindowingMode(1) success")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set windowing mode: ${e.message}")
        }
        
        return try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent.send(null, 0, null, null, null, null, options.toBundle())
            Log.i(TAG, "App launch initiated: $packageName on display $displayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
            false
        }
    }
    
    fun forceStopApp(packageName: String): Boolean {
        return try {
            val method = activityManager?.javaClass?.getDeclaredMethod(
                "forceStopPackage",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method?.invoke(activityManager, packageName, Process.myUserHandle().hashCode())
            displayPackageMap.entries.removeIf { it.value == packageName }
            Log.i(TAG, "Force stopped: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop app via system API, trying root", e)
            val result = RootUtils.executeCommand("am force-stop $packageName")
            result.success
        }
    }
    
    fun stopListening() {
        if (!isListenerRegistered || activityManager == null || taskStackListener == null) return
        
        try {
            val iListenerClass = Class.forName("android.app.ITaskStackListener")
            val unregisterMethod = activityManager!!.javaClass.getDeclaredMethod(
                "unregisterTaskStackListener",
                iListenerClass
            )
            unregisterMethod.invoke(activityManager, taskStackListener)
            
            isListenerRegistered = false
            Log.i(TAG, "TaskStackListener unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister TaskStackListener", e)
        }
    }
    
    private fun removePackageFromDisplays(packageName: String) {
        val displayId = displayPackageMap.entries.find { it.value == packageName }?.key
        if (displayId != null) {
            displayPackageMap.remove(displayId)
        }
    }
    
    fun destroy() {
        stopListening()
        displayPackageMap.clear()
        pendingLaunches.clear()
        onAppChanged = null
        onAppClosed = null
    }
}
