package com.templ.templ

import com.goide.sdk.GoSdkService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class TemplLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter) {

        val templConfigService = TemplSettings.getService(project)
        if (file.extension != "templ") return
        val executable = File(templConfigService.getTemplLspPath())
        if (!executable.exists()) return
        serverStarter.ensureServerStarted(TemplLspServerDescriptor(project, executable))
    }
}

private class TemplLspServerDescriptor(project: Project, val executable: File) :
    ProjectWideLspServerDescriptor(project, "templ") {
    private val wslSupport = TemplWslSupport.fromExecutablePath(executable.absolutePath)

    override fun isSupportedFile(file: VirtualFile) = file.extension == "templ"

    override fun createCommandLine(): GeneralCommandLine {
        val execPath = executable.absolutePath
        val settings = TemplSettings.getService(project).state

        val goPath = GoSdkService.getInstance(project).getSdk(null).executable?.parent?.path
        val parameters = mutableListOf<String>()
        if (settings.goplsLog.isNotEmpty()) {
            val goplsLog = wslSupport?.normalizeCommandPath(settings.goplsLog) ?: settings.goplsLog
            parameters.add("-goplsLog=$goplsLog")
        }
        if (settings.log.isNotEmpty()) {
            val logPath = wslSupport?.normalizeCommandPath(settings.log) ?: settings.log
            parameters.add("-log=$logPath")
        }
        if (settings.http.isNotEmpty()) parameters.add("-http=${settings.http}")
        if (settings.goplsRPCTrace) parameters.add("-goplsRPCTrace=true")
        if (settings.pprof) parameters.add("-pprof=true")
        if (settings.noPreload) parameters.add("-no-preload=true")
        if (settings.goplsRemote.isNotEmpty()) parameters.add("-gopls-remote=${settings.goplsRemote}")

        val cmd = if (wslSupport != null) {
            val linuxExecPath = wslSupport.toLinuxPath(execPath) ?: execPath
            val linuxGoPath = goPath?.let(wslSupport::toLinuxPath)
            wslSupport.createCommandLine(
                executablePath = linuxExecPath,
                subcommand = "lsp",
                arguments = parameters,
                workingDirectory = project.basePath,
                prependPath = linuxGoPath,
            )
        } else {
            GeneralCommandLine(execPath, "lsp").apply {
                addParameters(parameters)
                val currentPath = System.getenv("PATH").orEmpty()
                goPath?.let { withEnvironment("PATH", "$it${File.pathSeparator}$currentPath") }
            }
        }

        return cmd
    }

    override fun getFileUri(file: VirtualFile): String {
        return wslSupport?.toLinuxFileUri(file) ?: super.getFileUri(file)
    }

    override fun getFilePath(file: VirtualFile): String {
        return wslSupport?.toLinuxFilePath(file) ?: super.getFilePath(file)
    }

    override fun findFileByUri(fileUri: String): VirtualFile? {
        val idePath = wslSupport?.findIdePathByUri(fileUri)
        return idePath?.let(::findLocalFileByPath) ?: super.findFileByUri(fileUri)
    }
}

fun findGlobalTemplExecutable(): File? {
    PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("templ")?.let { return it }
    for (loc in templLocations)
        if (Files.exists(loc))
            return loc.toFile()
    return null
}

private val templLocations = arrayOf(
    System.getenv("GOBIN")?.let { Paths.get(it, "templ") },
    System.getenv("GOBIN")?.let { Paths.get(it, "templ.exe") },
    System.getenv("GOPATH")?.let { Paths.get(it, "bin", "templ") },
    System.getenv("GOPATH")?.let { Paths.get(it, "bin", "templ.exe") },
    System.getenv("GOROOT")?.let { Paths.get(it, "bin", "templ") },
    System.getenv("GOROOT")?.let { Paths.get(it, "bin", "templ.exe") },
    System.getenv("HOME")?.let { Paths.get(it, "bin", "templ") },
    System.getenv("HOME")?.let { Paths.get(it, "bin", "templ.exe") },
    System.getenv("HOME")?.let { Paths.get(it, "go", "bin", "templ") },
    System.getenv("HOME")?.let { Paths.get(it, "go", "bin", "templ.exe") },
    Paths.get("/usr/local/bin/templ"),
    Paths.get("/usr/bin/templ"),
    Paths.get("/usr/local/go/bin/templ"),
    Paths.get("/usr/local/share/go/bin/templ"),
    Paths.get("/usr/share/go/bin/templ"),
).filterNotNull()
