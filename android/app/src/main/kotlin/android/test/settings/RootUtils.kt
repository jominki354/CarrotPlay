package android.test.settings

import android.util.Log
import java.io.DataOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

object RootUtils {
    private const val TAG = "CarCarRootUtils"

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
