import jetbrains.mps.ide.newModuleDialogs.CopyModuleHelper
import jetbrains.mps.lang.migration.runtime.base.RefactoringRuntime
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
import jetbrains.mps.project.StandaloneMPSProject
import jetbrains.mps.project.structure.modules.Dependency
import jetbrains.mps.refactoring.Renamer
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory

fun updateIds(project: Project) {
    val replacedModules = mutableListOf<String>()
    val replacedModels = mutableListOf<String>()
    val mpsProject = project as StandaloneMPSProject
    project.projectModules.forEach { sModule ->
        val moduleName = sModule.moduleName!!
        val folder = project.getFolderFor(sModule)
        replacedModules.add(moduleName)
        replacedModels.addAll(sModule.models.map { it.reference.modelName })
        val module = cloneModule(sModule as AbstractModule, mpsProject)!!
        deleteModule(sModule, mpsProject)
        Renamer.renameModule(module, moduleName, mpsProject)
        project.setFolderFor(module, folder)
    }

    project.projectModules.filterIsInstance<AbstractModule>().forEach { module ->
        getBrokenDependencies(module, replacedModules)?.forEach { dependency ->
            module.removeDependency(dependency)
            module.addDependency(
                findModuleByName(project, dependency.moduleRef.moduleName!!).moduleReference,
                dependency.isReexport
            )
        }
        module.models.filterIsInstance<SModelInternal>().forEach { model ->
            model.importedLanguageIds().filter { replacedModules.contains(it.qualifiedName) }.forEach {
                model.deleteLanguageId(it)
                model.addLanguage(
                    MetaAdapterFactory.getLanguage(
                        findModuleByName(project, it.qualifiedName).moduleReference
                    )
                )
            }
            model.modelImports.filter { replacedModels.contains(it.modelName) }.forEach { reference ->
                model.deleteModelImport(reference)
                model.addModelImport(project.projectModels.find { it.reference.modelName == reference.modelName }?.reference!!)
            }
        }
        if (module is Language) {
            val languageDescriptor = module.moduleDescriptor
            languageDescriptor.runtimeModules.filter { replacedModules.contains(it.moduleName) }.forEach { reference ->
                languageDescriptor.runtimeModules.remove(reference)
                languageDescriptor.runtimeModules.add(project.projectModules.find { it.moduleName == reference.moduleName }?.moduleReference)
            }
        }
    }
}

private fun findModuleByName(
    project: StandaloneMPSProject,
    moduleName: String
) = project.projectModules.first { it.moduleName.equals(moduleName) }

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
    return CopyModuleHelper(
        mpsProject,
        sModule,
        sModule.moduleName + "_cloned",
        sModule.moduleSourceDir.parent,
        mpsProject.getFolderFor(sModule)
    ).copy()
}


