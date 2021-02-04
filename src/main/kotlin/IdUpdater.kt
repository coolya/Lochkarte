import com.intellij.openapi.diagnostic.Logger
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.ide.newModuleDialogs.CopyModuleHelper
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SConceptOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.*
import jetbrains.mps.project.structure.modules.Dependency
import jetbrains.mps.refactoring.Renamer
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.SNode
import jetbrains.mps.smodel.StaticReference
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapterById
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapterById
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import kotlin.test.fail

val logger = Logger.getInstance("ws.logv.Lochkarte.updateIds")

fun updateIds(project: Project) {
    val replacedModules = mutableListOf<String>()
    val replacedModels = mutableListOf<String>()
    val languageSubstitutes = hashMapOf<String, Language>()
    val modelSubstitutes = hashMapOf<SModelId, SModelReference>()

    val mpsProject = project as StandaloneMPSProject
    project.projectModules.forEach { sModule ->
        logger.info("updating module: ${sModule.moduleName}")
        try {
            val moduleName = sModule.moduleName!!
            val folder = project.getFolderFor(sModule)
            replacedModules.add(moduleName)
            replacedModels.addAll(sModule.models.map { it.reference.modelName })
            val newModule = cloneModule(sModule as AbstractModule, mpsProject)!!
            sModule.models.forEach { oldModel ->
                val substitute = newModule.models.find { it.name.simpleName == oldModel.name.simpleName }
                if (substitute == null) {
                    logger.warn("can't find substitute for model: ${oldModel.name}")
                } else {
                    modelSubstitutes[oldModel.modelId] = substitute.reference
                }
            }
            logger.info("deleting original modules")
            deleteModule(sModule, mpsProject)
            logger.info("renaming module copy")
            @Suppress("DEPRECATION")
            Renamer.renameModule(newModule, moduleName, mpsProject)
            project.setFolderFor(newModule, folder)
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
            languageSubstitutes[module.moduleName!!] = module
            logger.info("updating runtime models for language: ${module.moduleName}")
            val languageDescriptor = module.moduleDescriptor
            languageDescriptor.runtimeModules.filter { replacedModules.contains(it.moduleName) }.forEach { reference ->
                languageDescriptor.runtimeModules.remove(reference)
                val newReference =
                    project.projectModules.find { it.moduleName == reference.moduleName }?.moduleReference
                if (newReference == null) {
                    logger.error("can't find replacement for runtime module ${reference.moduleName}")
                } else {
                    languageDescriptor.runtimeModules.add(newReference)
                }
            }
        }
    }
    project.projectModels.forEach { model ->
        model.rootNodes.forEach { updateNode(languageSubstitutes, it) }
        model.rootNodes.forEach { updateReferences(modelSubstitutes, it) }
    }
}

fun updateReferences(
    modelSubstitutes: HashMap<SModelId, SModelReference>,
    node: org.jetbrains.mps.openapi.model.SNode
) {
    node.references.filterIsInstance<StaticReference>().forEach {
        val substitute = modelSubstitutes[it.targetSModelReference?.modelId]
        if (substitute != null) {
            it.setTargetSModelReference(substitute)
        }
    }
    node.children.forEach { child -> updateReferences(modelSubstitutes, child) }
}

fun updateNode(replacedLanguages: HashMap<String, Language>, node: org.jetbrains.mps.openapi.model.SNode) {
    node.children.forEach { updateNode(replacedLanguages, it) }

    val l = replacedLanguages[node.concept.language.qualifiedName]
    if (l != null) {
        val slang = MetaAdapterFactory.getLanguage(l.moduleReference)
        when (val concept = node.concept) {
            is SConceptAdapterById -> {

                val sConcept =
                    MetaAdapterFactory.getConcept(slang, concept.id.idValue, concept.name) as SConceptAdapterById
                val newInstance =
                    SConceptOperations.createNewNode(SNodeOperations.asInstanceConcept(sConcept)) as SNode
                newInstance.setId((node as SNode).nodeId)
                node.properties.forEach { oldProperty ->
                    when (oldProperty) {
                        is SPropertyAdapterById -> {
                            val sProperty = if (oldProperty.id.conceptId.idValue == sConcept.id.idValue) {
                                MetaAdapterFactory.getProperty(sConcept, oldProperty.id.idValue, oldProperty.name)
                            } else {
                                oldProperty
                            }
                            newInstance.setProperty(sProperty, node.getProperty(oldProperty))
                        }
                        else -> logger.error("unknown property type ${oldProperty.javaClass}")
                    }
                }

                node.children.forEach { child ->
                    when (val oldLink = child.containmentLink) {
                        is SContainmentLinkAdapterById -> {
                            val newLink = if (oldLink.id.conceptId.idValue == sConcept.id.idValue) {
                                MetaAdapterFactory.getContainmentLink(sConcept, oldLink.id.idValue, oldLink.name)
                            } else {
                                oldLink
                            }
                            node.removeChild(child)
                            newInstance.addChild(newLink, child)
                        }
                        else -> logger.error("unknown containment link type ${oldLink?.javaClass}")
                    }
                }

                node.references.forEach { oldReference ->
                    when (val oldLink = oldReference.link) {
                        is SReferenceLinkAdapterById -> {
                            val newLink = if (oldLink.id.conceptId.idValue == sConcept.id.idValue) {
                                MetaAdapterFactory.getReferenceLink(sConcept, oldLink.id.idValue, oldLink.name)
                            } else {
                                oldLink
                            }

                            newInstance.setReference(
                                newLink,
                                StaticReference(
                                    newLink,
                                    newInstance,
                                    oldReference.targetSModelReference,
                                    oldReference.targetNodeId,
                                    oldReference.resolveInfo
                                )
                            )
                        }
                        else -> logger.error("unknown reference link type: ${oldLink.javaClass}")
                    }
                }

                SNodeOperations.replaceWithAnother(node, newInstance)
            }
            else -> logger.error("Unknown concept type: ${concept.javaClass}")
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
    @Suppress("DEPRECATION") val newModuleFile =
        moduleTypeDirectory?.getDescendant(newModuleName)?.getDescendant(newModuleName + extension)

    return CopyModuleHelper(
        mpsProject,
        sModule,
        newModuleName,
        newModuleFile,
        mpsProject.getFolderFor(sModule)
    ).copy()
}


