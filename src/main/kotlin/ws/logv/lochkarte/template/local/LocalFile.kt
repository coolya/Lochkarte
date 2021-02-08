package ws.logv.lochkarte.template.local

import com.intellij.icons.AllIcons
import com.intellij.ide.util.BrowseFilesListener
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.FieldPanel
import com.intellij.ui.InsertPathAction
import com.intellij.util.ui.JBUI
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.StandaloneMPSProject
import jetbrains.mps.project.structure.project.ModulePath
import jetbrains.mps.workbench.dialogs.project.newproject.MPSProjectTemplate
import jetbrains.mps.workbench.dialogs.project.newproject.OtherProjectTemplate
import jetbrains.mps.workbench.dialogs.project.newproject.ProjectTemplatesGroup
import jetbrains.mps.workbench.dialogs.project.newproject.TemplateFiller
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent

class FromLocalFileSource : OtherProjectTemplate {
    private val settings = LocalSourceSettings { this.fireSettingsChanged() }
    override fun getIcon(): Icon? {
        return AllIcons.Nodes.IdeaProject
    }

    override fun getName(): String {
        return "From Local Template"
    }

    override fun getDescription(): String {
        return """Creates a new project from a local template.
            |
            |For more information on how templates work see the <a href="https://github.com/coolya/Lochkarte#templates"> documentation </a>
        """.trimMargin()
    }

    override fun getSettings(): JComponent {
        return settings
    }

    private fun checkTemplate(): String? {
        val locationPath = settings.templateLocationPath
        val location = File(locationPath)
        if (location.exists()) {
            return "Template not compatible: \n" + ws.logv.lochkarte.helper.checkTemplate(location).joinToString("\n")
        }
        return null
    }

    override fun checkSettings(): String? {
        return checkTemplate()
    }

    override fun getTemplateFiller(): TemplateFiller {
        return TemplateFiller { mpsProject ->
            val startupManager = StartupManager.getInstance(mpsProject.project)
            val minedModulePaths = mutableListOf<ModulePath>()
            startupManager.registerPreStartupActivity {
                mpsProject.modelAccess.executeCommand {
                    val templateLocationPath = settings.templateLocationPath
                    val projectRoot = mpsProject.projectFile
                    val templateRoot = File(templateLocationPath)
                    templateRoot.walk().forEach {
                        if (it.path.contains(File.separator + ".git" + File.separator)
                            || it.isMpsProjectFile("modules.xml")
                            || it.isMpsProjectFile("workspace.xml")
                            || it.isDirectory
                        ) {
                            return@forEach
                        }

                        it.copyTo(File(projectRoot, it.toRelativeString(templateRoot)))
                    }

                    val extensions = listOf(
                        MPSExtentions.DEVKIT,
                        MPSExtentions.GENERATOR,
                        MPSExtentions.LANGUAGE,
                        MPSExtentions.SOLUTION
                    )
                    projectRoot.walk()
                        .filter { !it.isDirectory && it.extension.isNotEmpty() && extensions.contains(it.extension.toLowerCase()) }
                        .forEach {
                            val modulePath = ModulePath(it.path, null)
                            minedModulePaths.add(modulePath)
                            (mpsProject as StandaloneMPSProject).projectDescriptor.addModulePath(modulePath)
                        }
                    mpsProject.repository.addRepositoryListener(object: SRepositoryListener {
                        override fun moduleAdded(module: SModule) {

                            if(module is AbstractModule) {
                                minedModulePaths.removeIf { it.path == module.descriptorFile?.path }
                            }

                            if(minedModulePaths.isEmpty()) {
                                mpsProject.repository.removeRepositoryListener(this)
                                mpsProject.modelAccess.runWriteAction {
                                    mpsProject.modelAccess.executeCommandInEDT {
                                        replaceMacros(mpsProject.project)
                                        updateIds(mpsProject)
                                    }
                                }
                            }
                        }

                        override fun beforeModuleRemoved(module: SModule) {
                            //noop
                        }

                        override fun moduleRemoved(module: SModuleReference) {
                            //noop
                        }

                        override fun commandStarted(repository: SRepository?) {
                            //noop
                        }

                        override fun commandFinished(repository: SRepository?) {
                            //noop
                        }

                        override fun updateStarted(repository: SRepository?) {
                            //noop
                        }

                        override fun updateFinished(repository: SRepository?) {
                            //noop
                        }

                        override fun repositoryCommandStarted(repository: SRepository?) {
                            //noop
                        }

                        override fun repositoryCommandFinished(repository: SRepository?) {
                            //noop
                        }
                    })
                    (mpsProject as StandaloneMPSProject).update()
                }
            }
        }
    }

    override fun setProjectPath(p0: String?) {
        //noop
    }

}

class LocalSourceSettings(changeListener: () -> Unit) : JPanel(GridBagLayout()) {
    private val templateLocation = JTextField()


    init {
        this.add(JLabel("Template Location:"), 0, 0.0)
        templateLocation.text = "Path"
        templateLocation.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                changeListener()
            }
        })

        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        InsertPathAction.addTo(templateLocation, descriptor)
        val listener = BrowseFilesListener(templateLocation, "Choose Template Directory", "", descriptor)
        val fieldPanel = FieldPanel(templateLocation, null, null, listener, EmptyRunnable.getInstance())
        FileChooserFactory.getInstance().installFileCompletion(fieldPanel.textField, descriptor, false, null)
        add(fieldPanel, 3, 0.0, JBUI.insetsBottom(5))
    }

    val templateLocationPath: String
        get() = templateLocation.text.trim()

    private fun add(component: Component, row: Int, rowWeight: Double) {
        add(component, row, rowWeight, JBUI.emptyInsets())
    }

    private fun add(component: Component, row: Int, rowWeight: Double, ins: Insets) {
        this.add(
            component,
            GridBagConstraints(
                0,
                row,
                1,
                1,
                1.0,
                rowWeight,
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.BOTH,
                ins,
                5,
                5
            )
        )
    }
}

private fun File.isMpsProjectFile(name: String) = this.path.contains(File.separator + ".mps" + File.separator + name)

