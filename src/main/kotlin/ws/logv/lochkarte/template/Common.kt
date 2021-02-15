package ws.logv.lochkarte.template

import com.intellij.openapi.diagnostic.Logger
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.StandaloneMPSProject
import jetbrains.mps.project.structure.project.ModulePath
import jetbrains.mps.project.structure.project.ProjectDescriptor
import jetbrains.mps.vfs.FileSystems
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

private val logger = Logger.getInstance("ws.logv.lochkarte")

fun fillProject(mpsProject: MPSProject, templateLocation: String) {
    val minedModulePaths = mutableListOf<ModulePath>()
    mpsProject.modelAccess.executeCommandInEDT {
        val projectRoot = mpsProject.projectFile
        val templateRoot = File(templateLocation)
        templateRoot.walk().forEach {
            if (it.path.contains(File.separator + ".git" + File.separator)
                || it.isMpsProjectFile("modules.xml")
                || it.isMpsProjectFile("workspace.xml")
                /*
                we need to avoid copying existing language descriptors because they will cause problems when
                when renaming modules if the are already compiled.
                */
                || it.isCompiledJavaFile
                || it.isDirectory
            ) {
                return@forEach
            }
            try {
                /*
                 We allow overriding existing files in the project. E.g. misc.xml, migrations.xml, etc
                 These files are usually created during initialization of the project but since they might store
                 state of the template we override them. If those files are under VFS or the files are used to store
                 the state via PersistentStateComponent we are fine and these will observe the file change and reload
                 the settings. In case the plugin is shipping homegrown logic to persist and load their state these might
                 end up not reloading the files. A possible solution to this would be to reload the complete project
                 after all the work is done.
                 */
                it.copyTo(File(projectRoot, it.toRelativeString(templateRoot)), overwrite = it.isMpsProjectFile())
            } catch (e: Exception) {
                logger.error("can't copy file", e)
            }
        }

        /*
        project update and module loading happens async after the module is added to the project
        we need to unblock the current thread to continue project loading
        Since there is no API to listen for #Project.update to complete we listen for the module added
        events until all modules we copied are added and then start the id update.
        */
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

        val extensions = listOf(
            MPSExtentions.DEVKIT,
            MPSExtentions.GENERATOR,
            MPSExtentions.LANGUAGE,
            MPSExtentions.SOLUTION
        )
        val projectDescriptor = ProjectDescriptor(mpsProject.name)
        projectRoot.walk()
            .filter { !it.isDirectory && it.extension.isNotEmpty() && extensions.contains(it.extension.toLowerCase()) }
            .forEach {
                val modulePath = ModulePath(it.path, null)
                /*
                workaround to trigger a vfs refresh because update on the project doesn't trigger a
                refresh in the VFS and will therefore discard the module path because it thinks it
                does not exit.
                The method is deprecated but afaik there is no other way than "getFile" to get a file
                vis it's path easily otherwise. VFSManager might be an alternative though.
                */
                @Suppress("DEPRECATION") val vfsFile = FileSystems.getDefault().getFile(it.path)
                vfsFile.mkdirs()
                minedModulePaths.add(modulePath)
                projectDescriptor.addModulePath(modulePath)
            }

        (mpsProject as StandaloneMPSProject).projectDescriptor = projectDescriptor
    }

}

private fun File.isMpsProjectFile(name: String? = null) = if (name != null) {
    this.path.endsWith(File.separator + ".mps" + File.separator + name)
} else {
    this.path.contains(File.separator + ".mps" + File.separator)
}

private val File.isCompiledJavaFile
    get() = this.extension == "class"

private enum class BitMaskFilePermission(private val mask: Int) {
    OWNER_READ(256), OWNER_WRITE(128), OWNER_EXECUTE(64), GROUP_READ(32), GROUP_WRITE(16),
    GROUP_EXECUTE(8), OTHERS_READ(4), OTHERS_WRITE(2), OTHERS_EXECUTE(1);

    val filePermission: PosixFilePermission = PosixFilePermission.valueOf(name)

    fun permitted(unixMode: Int): Boolean {
        return mask and unixMode == mask
    }
}

fun extractArchive(archive: File, destination: File, logger: Logger? = null) {

    val gzipCompressorInputStream = GzipCompressorInputStream(archive.inputStream())
    val tarArchiveInputStream = TarArchiveInputStream(gzipCompressorInputStream)
    var entry = tarArchiveInputStream.nextTarEntry
    while (entry != null) {
        if (entry.isDirectory) {
            val directory = File(destination, entry.name)
            val created = directory.mkdirs()
            if (!created) {
                logger?.error("can't create file: ${directory.path}")
                entry = tarArchiveInputStream.nextTarEntry
                continue
            }
            val mode = entry.mode
            val permissions =
                BitMaskFilePermission.values().filter { it.permitted(mode) }.map { it.filePermission }
            Files.setPosixFilePermissions(directory.toPath(), HashSet(permissions))
        } else {
            val file = File(destination, entry.name)
            val parentFile = file.parentFile
            if (!parentFile.exists()) {
                val created = parentFile.mkdirs()
                if (!created) {
                    logger?.error("can't create file: ${parentFile.path}")
                    entry = tarArchiveInputStream.nextTarEntry
                    continue
                }
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
