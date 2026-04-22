package com.templ.templ

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import java.io.File
import java.nio.charset.StandardCharsets


class TemplFormatter : AsyncDocumentFormattingService() {
    override fun getFeatures(): MutableSet<FormattingService.Feature> {
        return mutableSetOf(FormattingService.Feature.AD_HOC_FORMATTING)
    }

    override fun canFormat(psi: PsiFile): Boolean {
        return psi.fileType == TemplFileType
    }

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val formattingContext: FormattingContext = request.context
        val project = formattingContext.project
        val file = request.ioFile ?: return null

        val templConfigService = TemplSettings.getService(project)
        if (file.extension != "templ") return null
        val executable = File(templConfigService.getTemplLspPath())
        if (!executable.exists()) return null

        val params = SmartList<String>()
        val wslSupport = TemplWslSupport.fromExecutablePath(executable.absolutePath)
        val formattedFilePath = wslSupport?.toLinuxPath(file.absolutePath) ?: file.absolutePath

        params.add("fmt")
        params.add("-stdout")
        params.add(formattedFilePath)

        val commandLine: GeneralCommandLine = if (wslSupport != null) {
            wslSupport.createCommandLine(
                executablePath = wslSupport.toLinuxPath(executable.absolutePath) ?: executable.absolutePath,
                subcommand = "fmt",
                arguments = listOf("-stdout", formattedFilePath),
                workingDirectory = file.parent,
                prependPath = null,
            )
        } else {
            GeneralCommandLine()
                .withExePath(executable.absolutePath)
                .withParameters(params)
        }.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val handler = OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8))

        return object : FormattingTask {
            override fun run() {
                handler.addProcessListener(object : CapturingProcessAdapter() {
                    override fun processTerminated(event: ProcessEvent) {
                        val exitCode = event.exitCode
                        if (exitCode == 0) {
                            request.onTextReady(output.stdout)
                        } else {
                            request.onError("TemplFormatter", output.stderr)
                        }
                    }
                })

                handler.startNotify()
            }

            override fun cancel(): Boolean {
                handler.destroyProcess()
                return true
            }
        }
    }

    override fun getNotificationGroupId() = "TemplFormatter"

    override fun getName() = "TemplFormatter"
}
