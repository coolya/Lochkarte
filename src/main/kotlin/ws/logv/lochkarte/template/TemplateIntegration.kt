package ws.logv.lochkarte.template

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

class FromTemplateGroup : ProjectTemplatesGroup {
    override fun getName(): String {
        return "From Template"
    }

    override fun getTemplates(): MutableCollection<MPSProjectTemplate> {
        TODO("Not yet implemented")
    }
}

class FromLocalFileSource : OtherProjectTemplate {
    private val settings = LocalSourceSettings()
    override fun getIcon(): Icon? {
        return AllIcons.Nodes.IdeaProject
    }

    override fun getName(): String {
        return "From Local Template"
    }

    override fun getDescription(): String {
        return "Creates a new project from a local template."
    }

    override fun getSettings(): JComponent {
        return settings
    }

    private fun checkTemplate(): String? {
        //check if runtime or sandbox solution is in the language folder
        return null
    }

    override fun checkSettings(): String? {
        return checkTemplate()
    }

    override fun getTemplateFiller(): TemplateFiller {
        return TemplateFiller { project ->
            val startupManager = StartupManager.getInstance(project.project)
            val minedModulePaths = mutableListOf<ModulePath>()
            startupManager.registerPreStartupActivity {
                project.modelAccess.executeCommand {
                    val templateLocationPath = settings.templateLocationPath
                    val projectRoot = project.projectFile
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
                            (project as StandaloneMPSProject).projectDescriptor.addModulePath(modulePath)
                        }
                    project.repository.addRepositoryListener(object: SRepositoryListener {
                        override fun moduleAdded(module: SModule) {

                            if(module is AbstractModule) {
                                minedModulePaths.removeIf { it.path == module.descriptorFile?.path }
                            }

                            if(minedModulePaths.isEmpty()) {
                                project.repository.removeRepositoryListener(this)
                                project.modelAccess.runWriteAction {
                                    project.modelAccess.executeCommandInEDT {
                                        updateIds(project)
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
                    (project as StandaloneMPSProject).update()
                }
            }
        }
    }

    override fun setProjectPath(p0: String?) {
        //noop
    }

}

class LocalSourceSettings : JPanel(GridBagLayout()) {
    private val templateLocation = JTextField()


    init {
        this.add(JLabel("Template Location:"), 0, 0.0)
        templateLocation.text = "Path"
        templateLocation.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {

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

