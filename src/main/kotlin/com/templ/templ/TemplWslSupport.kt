package com.templ.templ

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI

internal class TemplWslSupport private constructor(
    private val distribution: String,
) {
    companion object {
        private val wslUncPathPattern = Regex("""^(?:\\\\|//)wsl(?:\.localhost|\$)(?:\\|/)([^\\/]+)(.*)$""")
        private val windowsDriveUriPathPattern = Regex("""^/[A-Za-z]:""")
        private val windowsDrivePathPattern = Regex("""^[A-Za-z]:""")

        fun fromExecutablePath(path: String): TemplWslSupport? {
            val match = wslUncPathPattern.matchEntire(path) ?: return null
            return TemplWslSupport(match.groupValues[1])
        }
    }

    fun toLinuxPath(path: String): String? {
        if (path.isBlank()) return null

        val uncMatch = wslUncPathPattern.matchEntire(path)
        if (uncMatch != null) {
            return normalizeLinuxSuffix(uncMatch.groupValues[2])
        }

        if (path.startsWith("/") && !path.startsWith("//")) {
            return path.replace('\\', '/')
        }

        return null
    }

    fun normalizeCommandPath(path: String): String {
        return toLinuxPath(path) ?: path
    }

    fun isWslBackedFile(file: VirtualFile): Boolean {
        return toLinuxPath(file.path) != null
    }

    fun toLinuxFilePath(file: VirtualFile): String? {
        return toLinuxPath(file.path)
    }

    fun toLinuxFileUri(file: VirtualFile): String? {
        val linuxFilePath = toLinuxFilePath(file) ?: return null
        return URI("file", "", linuxFilePath, null).toASCIIString()
    }

    fun findIdePathByUri(fileUri: String): String? {
        val uri = runCatching { URI(fileUri) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null

        val path = uri.path ?: return null
        return when {
            windowsDriveUriPathPattern.containsMatchIn(path) -> null
            windowsDrivePathPattern.containsMatchIn(path) -> null
            wslUncPathPattern.matches(path) -> normalizeIdeUncPath(path)
            path.startsWith("/") && !path.startsWith("//") -> "//wsl.localhost/$distribution${path.replace('\\', '/')}"
            else -> null
        }
    }

    fun createCommandLine(
        executablePath: String,
        subcommand: String,
        arguments: List<String>,
        workingDirectory: String?,
        prependPath: String?,
    ): GeneralCommandLine {
        val command = buildString {
            if (!prependPath.isNullOrBlank()) {
                append("PATH=")
                append(quoteForPosix(prependPath))
                append(":\"${'$'}PATH\"; export PATH; ")
            }
            append("exec ")
            append(quoteForPosix(executablePath))
            append(' ')
            append(quoteForPosix(subcommand))
            for (argument in arguments) {
                append(' ')
                append(quoteForPosix(argument))
            }
        }

        return GeneralCommandLine("wsl.exe").apply {
            toLinuxPath(workingDirectory.orEmpty())?.let {
                addParameters("--cd", it)
            }
            addParameters("-e", "sh", "-lc", command)
        }
    }

    private fun normalizeIdeUncPath(path: String): String {
        val uncMatch = wslUncPathPattern.matchEntire(path) ?: return path.replace('\\', '/')
        return "//wsl.localhost/${uncMatch.groupValues[1]}${normalizeLinuxSuffix(uncMatch.groupValues[2])}"
    }

    private fun normalizeLinuxSuffix(rawSuffix: String): String {
        if (rawSuffix.isEmpty()) return "/"

        val normalized = rawSuffix.replace('\\', '/')
        return if (normalized.startsWith("/")) normalized else "/$normalized"
    }

    private fun quoteForPosix(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}
