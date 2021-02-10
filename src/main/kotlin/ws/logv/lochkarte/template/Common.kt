package ws.logv.lochkarte.template

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.startup.StartupManager
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.StandaloneMPSProject
import jetbrains.mps.project.structure.project.ModulePath
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

fun fillProject(mpsProject: MPSProject, templateLocation: String) {
    val startupManager = StartupManager.getInstance(mpsProject.project)
    val minedModulePaths = mutableListOf<ModulePath>()
    startupManager.registerPostStartupActivity {
        mpsProject.modelAccess.executeCommand {
            val projectRoot = mpsProject.projectFile
            val templateRoot = File(templateLocation)
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

            // project update and module loading happens async after the module is added to the project
            // we need to unblock the current thread to continue project loading
            // Since there is no API to listen for #Project.update to complete we listen for the module added
            // events until all modules we copied are added and then start the id update.
            mpsProject.repository.addRepositoryListener(object : SRepositoryListener {
                override fun moduleAdded(module: SModule) {

                    if (module is AbstractModule) {
                        minedModulePaths.removeIf { it.path == module.descriptorFile?.path }
                    }

                    if (minedModulePaths.isEmpty()) {
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

private fun File.isMpsProjectFile(name: String) = this.path.contains(File.separator + ".mps" + File.separator + name)

private enum class BitMaskFilePermission(private val mask: Int) {
    OWNER_READ(256), OWNER_WRITE(128), OWNER_EXECUTE(64), GROUP_READ(32), GROUP_WRITE(16),
    GROUP_EXECUTE(8), OTHERS_READ(4), OTHERS_WRITE(2), OTHERS_EXECUTE(1);

    val filePermission: PosixFilePermission = PosixFilePermission.valueOf(name)

    fun permitted(unixMode: Int): Boolean {
        return mask and unixMode == mask
    }
}

fun extractArchive(archive: File, destination: File, logger: Logger) {

    val gzipCompressorInputStream = GzipCompressorInputStream(archive.inputStream())
    val tarArchiveInputStream = TarArchiveInputStream(gzipCompressorInputStream)
    var entry = tarArchiveInputStream.nextTarEntry
    while (entry != null) {

        if (entry.isDirectory) {
            val directory = File(destination, entry.name)
            val created = directory.mkdirs()
            if (!created) {
                logger.error("can't create file: ${directory.path}")
            }
            val mode = entry.mode
            val permissions =
                BitMaskFilePermission.values().filter { it.permitted(mode) }.map { it.filePermission }
            Files.setPosixFilePermissions(directory.toPath(), HashSet(permissions))
        } else {
            val file = File(destination, entry.name)
            val created = file.parentFile.mkdirs()
            if (!created) {
                logger.error("can't create file: ${file.parentFile.path}")
                continue
            }
            Files.write(file.toPath(), tarArchiveInputStream.readAllBytes())
            val mode = entry.mode
            val permissions =
                BitMaskFilePermission.values().filter { it.permitted(mode) }.map { it.filePermission }
            Files.setPosixFilePermissions(file.toPath(), HashSet(permissions))
        }

        entry = tarArchiveInputStream.nextTarEntry
    }
}
