package ws.logv.lochkarte.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.project.ProjectHelper
import ws.logv.lochkarte.helper.checkTemplate
import java.io.File

class CheckProjectAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val basePath = e.project?.basePath
        if(basePath != null) {
            val errors = checkTemplate(File(basePath))
            if(errors.isNotEmpty()) {
                Messages.showWarningDialog(e.project, """The validation found the the following problems:
                    | ${errors.joinToString("\n")}""".trimMargin(), "Project not Usable as a Template")
            } else {
                Messages.showInfoMessage(e.project, "No Problems found.", "Project Usable as a Template")
            }
        }
    }
}