package com.murphy.core

import com.android.resources.ResourceType
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile

object ResourceGenerator : AbstractGenerator() {
    override val name: String get() = "Resource"

    val includedAttrType = arrayOf(
        ResourceType.STRING, ResourceType.INTEGER, ResourceType.ARRAY,
        ResourceType.STYLE, ResourceType.COLOR, ResourceType.DIMEN
    )

    val excludedFileType = arrayOf(
        ResourceType.STRING, ResourceType.INTEGER, ResourceType.ATTR,
        ResourceType.STYLE, ResourceType.COLOR, ResourceType.DIMEN,
        ResourceType.LAYOUT, ResourceType.MIPMAP
    )

    override fun process(first: Project, second: ProgressIndicator, data: List<PsiNamedElement>) {
        super.process(first, second, data)
        processAttribute(data)
        processFile(data)
    }

    private fun processAttribute(data: List<PsiNamedElement>) {
        if (config.resourceRule.isNotEmpty()) {
            val xmlAttrList = service.dumbReadAction {
                data.filterIsInstance<XmlFile>().map {
                    it.childrenDfsSequence()
                        .filterIsInstance<XmlAttributeValue>()
                        .mapNotNull { ResourceReferencePsiElement.create(it) }
                        .toList()
                }.flatten().distinctBy { it.resourceReference.resourceUrl }
            }
            service.dumbReadAction {
                xmlAttrList.filter { it.resourceReference.resourceType == ResourceType.ID }
                    .alsoReset("IdResource")
            }.forEach {
                val psiReferences = service.dumbReadAction { it.findIdReference(scope) }
                val delegate = it.delegate
                val newName = config.randomResourceName
                if (delegate is XmlAttributeValue) {
                    delegate.renameX(newName, "IdAttribute")
                    psiReferences?.run {
                        if (isNotEmpty()) {
                            val newRefName = DataBindingUtil.convertAndroidIdToJavaFieldName(newName)
                            println(String.format("[IdBinding] >>> %s", newRefName))
                            handleReferenceRename(project, newRefName)
                        }
                    }
                }
                increase()
            }
            service.dumbReadAction {
                xmlAttrList.filter { includedAttrType.contains(it.resourceReference.resourceType) }
                    .map { it.delegate }.filterIsInstance<XmlAttributeValue>()
                    .alsoReset("Resource")
            }.forEach {
                it.renameX(config.randomResourceName, "Attribute")
                increase()
            }
        }
    }

    private fun processFile(data: List<PsiNamedElement>) {
        if (config.resFileRule.isNotEmpty()) {
            val xmlFileList = service.dumbReadAction {
                data.filterIsInstance<XmlFile>()
                    .mapNotNull { ResourceReferencePsiElement.create(it) }
            }
            service.dumbReadAction {
                xmlFileList.filter { it.resourceReference.resourceType == ResourceType.LAYOUT }
                    .alsoReset("LayoutResource")
            }.forEach {
                val psiReferences = service.dumbReadAction { it.findLayoutReference(scope) }
                val delegate = it.delegate
                val newName = config.randomResFileName
                if (delegate is XmlFile) {
                    delegate.rename(newName, "Layout")
                    psiReferences?.run {
                        if (isNotEmpty()) {
                            val newRefName = DataBindingUtil.convertFileNameToJavaClassName(newName) + "Binding"
                            println(String.format("[LayoutBinding] >>> %s", newRefName))
                            handleReferenceRename(project, newRefName)
                        }
                    }
                }
                increase()
            }
            service.dumbReadAction {
                xmlFileList.filter { !excludedFileType.contains(it.resourceReference.resourceType) }
                    .map { it.delegate }.filterIsInstance<XmlFile>()
                    .alsoReset("FileResource")
            }.forEach {
                it.rename(config.randomResFileName, "File")
                increase()
            }
        }
    }
}