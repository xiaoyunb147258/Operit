package com.ai.assistance.operit.ui.features.termux

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * Termux 运行时：负责环境目录解析与终端会话创建。
 *
 * 目录布局（应用私有）：
 *   filesDir/termux/home       -> HOME
 *   filesDir/termux/usr        -> PREFIX（bootstrap 安装到这里）
 *   filesDir/termux/tmp        -> TMPDIR
 *
 * bootstrap 由 Termux 的 termux-bootstrap native 库提供（编译进 APK 的 assets 里）。
 */
object TermuxRuntime {

    private const val TRANSCRIPT_ROWS = 50000

    @Volatile
    private var initialized = false

    /** Termux 前缀根目录 */
    fun prefixDir(context: Context): File = File(context.filesDir, "termux/usr")

    /** Termux HOME */
    fun homeDir(context: Context): File = File(context.filesDir, "termux/home")

    /** TMPDIR */
    fun tmpDir(context: Context): File = File(context.filesDir, "termux/tmp")

    /** 环境是否已就绪（PREFIX/bin/sh 存在） */
    fun isReady(context: Context): Boolean = File(prefixDir(context), "bin/sh").exists()

    /**
     * 初始化目录。bootstrap 解压由 Termux 的 TermuxInstaller 负责，
     * 这里只确保基础目录存在。
     */
    @Synchronized
    fun ensureDirs(context: Context) {
        homeDir(context).mkdirs()
        tmpDir(context).mkdirs()
        prefixDir(context).mkdirs()
        initialized = true
    }

    /**
     * 创建新的终端会话。
     *
     * @return 会话对象；若环境未就绪返回 null
     */
    fun createSession(
        context: Context,
        client: TerminalSessionClient = TermuxSessionClient()
    ): TerminalSession? {
        ensureDirs(context)

        val prefix = prefixDir(context)
        val home = homeDir(context)
        val tmp = tmpDir(context)

        val shell = File(prefix, "bin/login")
        if (!shell.exists()) {
            // bootstrap 尚未安装，无法创建可执行会话
            return null
        }

        val env = arrayOf(
            "HOME=${home.absolutePath}",
            "PREFIX=${prefix.absolutePath}",
            "TMPDIR=${tmp.absolutePath}",
            "PATH=${File(prefix, "bin").absolutePath}:/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "SHELL=${shell.absolutePath}",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system"
        )

        return TerminalSession(
            shell.absolutePath,
            home.absolutePath,
            arrayOf("-l"),
            env,
            TRANSCRIPT_ROWS,
            client
        )
    }
}
