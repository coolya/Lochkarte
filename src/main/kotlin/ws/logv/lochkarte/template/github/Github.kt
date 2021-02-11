package ws.logv.lochkarte.template.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.util.io.HttpRequests
import com.intellij.util.ui.JBUI
import jetbrains.mps.project.MPSProject
import jetbrains.mps.workbench.dialogs.project.newproject.OtherProjectTemplate
import jetbrains.mps.workbench.dialogs.project.newproject.TemplateFiller
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import ws.logv.lochkarte.template.extractArchive
import ws.logv.lochkarte.template.fillProject
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import javax.swing.*
import javax.swing.event.DocumentEvent
import java.nio.file.attribute.PosixFilePermission


class GithubSourceTemplate : OtherProjectTemplate {
    private val settings = GithubSourceSettings { this.fireSettingsChanged() }
    private val logger = Logger.getInstance("ws.logv.lochkarte.template.github")

    override fun getIcon(): Icon? {
        return AllIcons.Nodes.IdeaProject
    }

    override fun getName(): String {
        return "From Github Template"
    }

    override fun getDescription(): String {
        return """Creates a new project from a template hosted on Github.
            |
            |For more information on how templates work see the <a href="https://github.com/coolya/Lochkarte#templates"> documentation </a>
        """.trimMargin()
    }

    override fun getSettings(): JComponent {
        return settings
    }

    override fun getTemplateFiller(): TemplateFiller {
        return TemplateFiller { mpsProject ->

            val startupManager = StartupManager.getInstance(mpsProject.project)
            startupManager.registerStartupActivity {
                // we should never end up in the error case here because checkSettings is invoked prior to
                // creating the project and will block project creation if an error message is returned.
                // This is why we don't bother to inform the user.
                val url = when (val checkResult = checkUrl(settings.templateUrl)) {
                    is CheckResult.Error -> return@registerStartupActivity
                    is CheckResult.Success -> checkResult.url
                }

                val archive: File
                try {
                    archive = download(mpsProject.name, url)
                } catch (e: IOException) {
                    Messages.showErrorDialog(
                        mpsProject.project, """Error downloading template: 
                        |${e.message}
                        | 
                        |Make sure your link points to a repository on Github/ Github Enterprise.
                        |Authentication to Github is not supported. See the documentation for more 
                        |details and troubleshooting help visit the <a href="https://github.com/coolya/Lochkarte#hosted-templates">documentation</a>.
                        |
                        |Only an empty project has been created.""".trimMargin(), "Error Downloading Template"
                    )
                    return@registerStartupActivity
                }

                val tempDirectory = Files.createTempDirectory("template").toFile()
                extractArchive(archive, tempDirectory, logger)
                fillProject(mpsProject, tempDirectory.listFiles()!!.first().path)
            }
        }
    }

    override fun checkSettings(): String? {
        // ideally we would like to probe the url here to check if the url is indeed a
        // github instance. We can't do this because "checkSettings" is invoked on the AWT event queue
        // and network requests on that thread would block the UI.
        // Going to send a PR to MPS that will allow checking outside of AWT but this won't be available
        // until at last 2021.1
        // We can't only check by hostname because  for github enterprise
        // this could be any other hostname than github.com. There is also no way present a
        // warning to the user from an extension.
        return when (val checkResult = checkUrl(settings.templateUrl)) {
            is CheckResult.Error -> checkResult.message
            is CheckResult.Success -> null
        }
    }

    override fun setProjectPath(p0: String?) {
        // noop
    }
}

sealed class CheckResult {
    data class Success(val url: String) : CheckResult()
    data class Error(val message: String) : CheckResult()
}

fun checkUrl(url: String): CheckResult {
    val parsedUrl = try {
        URL(url)
    } catch (e: MalformedURLException) {
        return CheckResult.Error("Invalid Url")
    }

    val pathSegments = parsedUrl.path.split("/").let { it.subList(1, it.size) }
    if (pathSegments.size < 2) {
        return CheckResult.Error("Url seems not to point to a repository.")
    }
    val hostPart = if(parsedUrl.host == "github.com") {
        "api.github.com"
    } else {
        //github enterprise hosts the api on the same host but the with api/v3 prefix
        // https://docs.github.com/en/enterprise-server@3.0/rest/reference/enterprise-admin
        "${parsedUrl.host}/api/v3"
    }

    // third segment is ignored, the fourth is usually the branch name.
    return if (pathSegments.size == 2 || pathSegments.size == 3) {
        CheckResult.Success("https://$hostPart/repos/${pathSegments[0]}/${pathSegments[1]}/tarball/")
    } else if (pathSegments.size == 4 && pathSegments[2] == "tree") {
        CheckResult.Success("https://$hostPart/repos/${pathSegments[0]}/${pathSegments[1]}/tarball/${pathSegments[3]}")
    } else {
        CheckResult.Error("Malformed URL.")
    }
}

fun download(projectName: String, url: String) : File   {
    val archive = Files.createTempFile(projectName, "tar.gz").toFile()
    HttpRequests.request(url).productNameAsUserAgent().saveToFile(archive, null)
    return archive
}

class GithubSourceSettings(changeListener: () -> Unit) : JPanel(GridBagLayout()) {
    private val templateUrlField: JTextField

    init {
        this.add(JLabel("Template Location:"), 0, 0.0)
        templateUrlField = JTextField()
        templateUrlField.name = "Url"
        templateUrlField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(p0: DocumentEvent) {
                changeListener()
            }
        })
        this.add(templateUrlField, 1, 0.0, JBUI.insetsBottom(5))
    }

    val templateUrl: String
        get() = templateUrlField.text.trim()

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

