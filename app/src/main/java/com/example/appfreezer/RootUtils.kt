package com.example.appfreezer

import java.io.BufferedReader
import java.io.InputStreamReader

object RootUtils {

    fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.write("id\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()

            val exitCode = process.waitFor()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            exitCode == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun executeRootCommand(command: String): Pair<Boolean, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()

            val output = StringBuilder()
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            while (errorStream.readLine().also { line = it } != null) {
                output.append("ERROR: $line").append("\n")
            }

            inputStream.close()
            errorStream.close()

            Pair(exitCode == 0, output.toString())
        } catch (e: Exception) {
            Pair(false, "执行失败: ${e.message}")
        }
    }

    /**
     * 封印应用 - 彻底禁止启动
     */
    fun freezeApp(packageName: String): Pair<Boolean, String> {
        // 1. 强制停止应用
        executeRootCommand("am force-stop $packageName")

        // 2. 禁用应用
        val (disableSuccess, disableOutput) = executeRootCommand("pm disable-user --user 0 $packageName")

        return if (disableSuccess) {
            Pair(true, "应用已封印")
        } else {
            Pair(false, "封印失败: $disableOutput")
        }
    }

    /**
     * 解冻应用 - 恢复正常使用
     */
    fun unfreezeApp(packageName: String): Pair<Boolean, String> {
        // 启用应用
        val (enableSuccess, enableOutput) = executeRootCommand("pm enable $packageName")

        return if (enableSuccess) {
            Pair(true, "应用已解冻，请在应用抽屉中查看")
        } else {
            Pair(false, "解冻失败: $enableOutput")
        }
    }

    /**
     * 检查应用是否被封印
     */
    fun isAppFrozen(packageName: String): Boolean {
        val (success, output) = executeRootCommand("pm list packages -d | grep $packageName")
        return success && output.contains(packageName)
    }

    /**
     * 获取应用状态
     */
    fun getAppPermissionStatus(packageName: String): Map<String, String> {
        val status = mutableMapOf<String, String>()

        // 检查是否被禁用
        val (disabledSuccess, disabledOutput) = executeRootCommand("pm list packages -d | grep $packageName")
        val isDisabled = disabledSuccess && disabledOutput.contains(packageName)

        // 检查是否启用
        val (enabledSuccess, enabledOutput) = executeRootCommand("pm list packages -e | grep $packageName")
        val isEnabled = enabledSuccess && enabledOutput.contains(packageName)

        status["应用状态"] = when {
            isDisabled -> "已封印 🔒"
            isEnabled -> "正常运行 ✅"
            else -> "未知状态 ❓"
        }

        return status
    }

    /**
     * 强制停止应用
     */
    fun forceStopApp(packageName: String): Pair<Boolean, String> {
        val command = "am force-stop $packageName"
        return executeRootCommand(command)
    }

    /**
     * 获取所有用户应用包名
     */
    fun getAllUserPackages(): List<String> {
        val commands = arrayOf(
            "pm list packages -3",      // 第三方应用
            "pm list packages -d -3"    // 被禁用的第三方应用
        )

        val allPackages = mutableSetOf<String>()

        for (command in commands) {
            val (success, output) = executeRootCommand(command)
            if (success) {
                output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.substring(8) }
                    .forEach { allPackages.add(it) }
            }
        }

        return allPackages.toList()
    }

    // 兼容旧方法名
    fun disableApp(packageName: String) = freezeApp(packageName)
    fun enableApp(packageName: String) = unfreezeApp(packageName)
    fun isAppDisabled(packageName: String) = isAppFrozen(packageName)
}