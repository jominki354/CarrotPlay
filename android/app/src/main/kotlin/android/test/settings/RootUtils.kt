package android.test.settings

import android.util.Log
import java.io.DataOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * ROOT 명령 실행 유틸리티
 * 
 * 성능 최적화:
 * - 지속적인 su 세션 유지 (매번 새로 생성하지 않음)
 * - 비동기 명령 실행 지원 (fire-and-forget)
 * - 명령 큐를 통한 순차 처리
 */
object RootUtils {
    private const val TAG = "CarCarRootUtils"
    
    // 지속적인 su 세션
    private var persistentProcess: Process? = null
    private var persistentOutputStream: DataOutputStream? = null
    private val sessionLock = Object()
    private val isSessionActive = AtomicBoolean(false)
    
    // 비동기 명령 큐
    private val commandQueue = ConcurrentLinkedQueue<String>()
    private val isProcessingQueue = AtomicBoolean(false)

    fun isRootAvailable(): Boolean {
        return executeCommand("echo root_check").success
    }

    fun requestRoot(): Boolean {
        // Simply executing "su" triggers the Magisk prompt
        return executeCommand("id").success
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
    
    /**
     * 지속적인 su 세션 시작
     * 앱 시작 시 한 번만 호출
     */
    fun initPersistentSession(): Boolean {
        synchronized(sessionLock) {
            if (isSessionActive.get()) return true
            
            try {
                persistentProcess = Runtime.getRuntime().exec("su")
                persistentOutputStream = DataOutputStream(persistentProcess!!.outputStream)
                isSessionActive.set(true)
                Log.i(TAG, "Persistent su session started")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start persistent su session", e)
                return false
            }
        }
    }
    
    /**
     * 지속적인 세션 종료
     */
    fun closePersistentSession() {
        synchronized(sessionLock) {
            try {
                persistentOutputStream?.writeBytes("exit\n")
                persistentOutputStream?.flush()
                persistentOutputStream?.close()
                persistentProcess?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
            persistentProcess = null
            persistentOutputStream = null
            isSessionActive.set(false)
            Log.i(TAG, "Persistent su session closed")
        }
    }
    
    /**
     * 비동기 명령 실행 (결과 대기 안 함 - fire and forget)
     * 프리셋 전환, 앱 실행 등 빠른 응답이 필요한 경우 사용
     */
    fun executeCommandAsync(command: String) {
        commandQueue.offer(command)
        processQueueIfNeeded()
    }
    
    private fun processQueueIfNeeded() {
        if (isProcessingQueue.compareAndSet(false, true)) {
            thread {
                try {
                    while (true) {
                        val cmd = commandQueue.poll() ?: break
                        executeCommandFast(cmd)
                    }
                } finally {
                    isProcessingQueue.set(false)
                    // 큐에 새로운 명령이 추가되었을 수 있음
                    if (commandQueue.isNotEmpty()) {
                        processQueueIfNeeded()
                    }
                }
            }
        }
    }
    
    /**
     * 빠른 명령 실행 (지속 세션 사용, 출력 대기 최소화)
     */
    fun executeCommandFast(command: String): Boolean {
        // 지속 세션이 없으면 시작
        if (!isSessionActive.get()) {
            initPersistentSession()
        }
        
        synchronized(sessionLock) {
            try {
                persistentOutputStream?.let { os ->
                    os.writeBytes("$command\n")
                    os.flush()
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fast command failed, resetting session", e)
                closePersistentSession()
                initPersistentSession()
            }
        }
        return false
    }

    /**
     * 동기 명령 실행 (결과 필요한 경우)
     */
    fun executeCommand(command: String): CommandResult {
        Log.d(TAG, "Executing root command: $command")
        var process: Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null
        var errorReader: BufferedReader? = null

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val output = StringBuilder()
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val error = StringBuilder()
            errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }

            process.waitFor()
            val exitCode = process.exitValue()
            
            Log.d(TAG, "Command finished with exit code: $exitCode")
            if (exitCode != 0) {
                Log.e(TAG, "Command error output: $error")
            }

            return CommandResult(exitCode == 0, output.toString().trim(), error.toString().trim())

        } catch (e: Exception) {
            Log.e(TAG, "Exception executing root command", e)
            return CommandResult(false, "", e.message ?: "Unknown error")
        } finally {
            try {
                os?.close()
                reader?.close()
                errorReader?.close()
                process?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}