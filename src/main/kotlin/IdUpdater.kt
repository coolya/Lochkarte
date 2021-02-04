import com.intellij.openapi.diagnostic.Logger
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.ide.newModuleDialogs.CopyModuleHelper
import jetbrains.mps.lang.migration.runtime.base.RefactoringRuntime
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.*
import jetbrains.mps.project.structure.modules.Dependency
import jetbrains.mps.refactoring.Renamer
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import java.io.File
import kotlin.test.fail

val logger = Logger.getInstance("ws.logv.Fernsprecher.updateIds")

fun updateIds(project: Project) {
    val replacedModules = mutableListOf<String>()
    val replacedModels = mutableListOf<String>()
    val mpsProject = project as StandaloneMPSProject
    project.projectModules.forEach { sModule ->
        logger.info("updating module: ${sModule.moduleName}")
        try {
            val moduleName = sModule.moduleName!!
            val folder = project.getFolderFor(sModule)
            replacedModules.add(moduleName)
            replacedModels.addAll(sModule.models.map { it.reference.modelName })
            val module = cloneModule(sModule as AbstractModule, mpsProject)!!
            logger.info("deleting original modules")
            deleteModule(sModule, mpsProject)
            logger.info("renaming module copy")
            @Suppress("DEPRECATION")
            Renamer.renameModule(module, moduleName, mpsProject)
            project.setFolderFor(module, folder)
        } catch (t: Throwable) {
            logger.error("error updating module ${sModule.moduleName}", t)
        }
    }

    project.projectModules.filterIsInstance<AbstractModule>().forEach { module ->
        getBrokenDependencies(module, replacedModules)?.forEach { dependency ->
            logger.info("${module.moduleName}: fixing broken dependency on ${dependency.moduleRef.moduleName}")
            module.removeDependency(dependency)
            val newDependencyRef = findModuleByName(project, dependency.moduleRef.moduleName)?.moduleReference
            if (newDependencyRef == null) {
                logger.error("${module.moduleName}: can't find replacement.")
            } else {
                module.addDependency(
                    newDependencyRef,
                    dependency.isReexport
                )
            }
        }
        module.models.filterIsInstance<SModelBase>().forEach { model ->
            logger.info("${model.name.longName}: updating used languages.")
            model.importedLanguageIds().filter { replacedModules.contains(it.qualifiedName) }.forEach {
                model.deleteLanguageId(it)
                val newReference = findModuleByName(project, it.qualifiedName)?.moduleReference
                if (newReference == null) {
                    logger.error("can't find replacement for language ${it.qualifiedName}")
                } else {
                    val language = MetaAdapterFactory.getLanguage(newReference)
                    model.addLanguage(language)
                }

            }
            logger.info("${model.name.longName}: updating imports.")
            model.modelImports.filter { replacedModels.contains(it.modelName) }.forEach { reference ->
                model.deleteModelImport(reference)
                val newReference =
                    project.projectModels.find { it.reference.modelName == reference.modelName }?.reference
                if (newReference == null) {
                    logger.error("can't find replacement for import ${reference.modelName}")
                } else {
                    model.addModelImport(newReference)
                }
            }
        }
        if (module is Language) {
            logger.info("updating runtime models for language: ${module.moduleName}")
            val languageDescriptor = module.moduleDescriptor
            languageDescriptor.runtimeModules.filter { replacedModules.contains(it.moduleName) }.forEach { reference ->
                languageDescriptor.runtimeModules.remove(reference)
                val newReference = project.projectModules.find { it.moduleName == reference.moduleName }?.moduleReference
                if(newReference == null){
                    logger.error("can't find replacement for runtime module ${reference.moduleName}")
                } else {
                    languageDescriptor.runtimeModules.add(newReference)
                }
            }
        }
    }
}

private fun findModuleByName(
    project: StandaloneMPSProject,
    moduleName: String?
) = project.projectModules.find { it.moduleName.equals(moduleName) }

private fun getBrokenDependencies(module: AbstractModule, replacedModules: MutableList<String>): List<Dependency>? {
    return module.moduleDescriptor?.dependencies?.filter { replacedModules.contains(it.moduleRef.moduleName) }
}

private fun deleteModule(sModule: AbstractModule, mpsProject: Project) {
    val sourceDir = sModule.moduleSourceDir
    ModuleDeleteHelper(mpsProject).deleteModules(listOf(sModule), false, true)

    // WORKAROUND - this is a workaround for a bug in MPS delete helper. See https://youtrack.jetbrains.com/issue/MPS-30226
    sourceDir.deleteIfExists()
}

private fun cloneModule(sModule: AbstractModule, mpsProject: StandaloneMPSProject): AbstractModule? {
    val moduleTypeDirectory = sModule.moduleSourceDir.parent
    val extension = when (sModule) {
        is Language -> MPSExtentions.DOT_LANGUAGE
        is Solution -> MPSExtentions.DOT_SOLUTION
        else -> fail("unknown module type")
    }
    val newModuleName = sModule.moduleName + "_cloned"
    @Suppress("DEPRECATION") val newModuleFile = moduleTypeDirectory?.getDescendant(newModuleName)?.getDescendant(newModuleName + extension)

    return CopyModuleHelper(
        mpsProject,
        sModule,
        newModuleName,
        newModuleFile,
        mpsProject.getFolderFor(sModule)
    ).copy()
}


