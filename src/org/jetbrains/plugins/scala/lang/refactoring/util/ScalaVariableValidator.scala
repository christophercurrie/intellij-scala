package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScClass}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import params.{ScParameters, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

/**
* User: Alexander Podkhalyuzin
* Date: 24.06.2008
*/

class ScalaVariableValidator(introduceVariableBase: ConflictsReporter,
                             myProject: Project,
                             selectedExpr: ScExpression,
                              noOccurrences: Boolean,
                             enclosingContainerAll: PsiElement, enclosingOne: PsiElement) extends NameValidator {
  def getProject(): Project = {
    myProject
  }

  def isOK(dialog: NamedDialog): Boolean = {
    if (noOccurrences) return true
    val name = dialog.getEnteredName
    val allOcc = dialog.isReplaceAllOccurrences
    val conflicts = isOKImpl(name, allOcc)
    conflicts.length == 0 || introduceVariableBase.reportConflicts(conflicts, myProject)
  }

  def isOKImpl(name: String, allOcc: Boolean): Array[String] = {
    val enclosingContainer: PsiElement = if (allOcc) enclosingContainerAll else enclosingOne
    if (enclosingContainer == null) return Array()
    val buf = new ArrayBuffer[String]
    buf ++= validateDown(enclosingContainer, name, allOcc)
    buf ++= validateUp(enclosingContainer, name)
    var cl = enclosingContainer
    while (cl != null && !cl.isInstanceOf[ScClass] && !cl.isInstanceOf[ScTrait]) cl = cl.getParent
    if (cl != null) {
      cl match {
        case x: ScTypeDefinition => {
          for (member <- x.members) {
            member match {
              case x: ScVariable => for (el <- x.declaredElements if el.name == name)
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.field", el.name)
              case x: ScValue => for (el <- x.declaredElements if el.name == name)
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.field", el.name)
              case _ =>
            }
          }
          for (function <- x.functions) {
            function match {
              case x: ScFunction if x.name == name && x.parameters.size == 0 =>
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.field", x.name)
              case _ =>
            }
          }
        }
      }
    }
    buf.toArray
  }

  private def validateUp(element: PsiElement, name: String): Array[String] = {
    val buf = new ArrayBuffer[String]
    val parent = if (element.getPrevSibling != null) element.getPrevSibling else element.getParent
    element match {
      case x: ScVariableDefinition => {
        val elems = x.declaredElements
        for (elem <- elems) {
          if (elem.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", elem.name)
          }
        }
      }
      case x: ScPatternDefinition => {
        val elems = x.declaredElements
        for (elem <- elems) {
          if (elem.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", elem.name)
          }
        }
      }
      case x: ScParameters => {
        for (parameter <- x.params)
        if (parameter.name == name) {
          buf += ScalaBundle.message("introduced.variable.will.conflict.with.parameter", parameter.name)
        }
      }
      case x: ScFunctionDefinition => {
        if (x.name == name && x.parameters.size == 0) {
          buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", x.name)
        }
      }
      case _ =>
    }
    parent match {
      case _: ScTemplateBody | null =>
      case _ => parent.getParent match {
        case _: ScTemplateBody =>
        case _ => buf ++= validateUp(parent, name)
      }
    }
    buf.toArray
  }

  private def validateDown(element: PsiElement, name: String, allOcc: Boolean): Array[String] = {
    val enclosingContainer: PsiElement = if (allOcc) enclosingContainerAll else enclosingOne
    val buf = new ArrayBuffer[String]
    for (child <- element.getChildren) {
      child match {
        case x: ScParameter => {
          if (x.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.parameter", x.name)
          }
        }
        case x: ScFunctionDefinition => {
          if (x.name == name && x.parameters.size == 0) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", x.name)
          }
        }
        case x: ScBindingPattern => {
          if (x.name == name) {
            buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", x.name)
          }
        }
        case _ =>
      }
    }
    if (element != enclosingContainer)
      for (child <- element.getChildren) {
        buf ++= validateDown(child, name, allOcc)
      }
    else {
      var from = {
        var parent: PsiElement = if (allOcc) {
          selectedExpr //todo:
        } else {
          selectedExpr
        }
        if (parent != enclosingContainer)
          while (parent.getParent != null && parent.getParent != enclosingContainer) parent = parent.getParent
        else parent = parent.getFirstChild
        parent
      }
      var fromDoubles = from.getPrevSibling
      while (fromDoubles != null) {
        fromDoubles match {
          case x: ScVariableDefinition => {
            val elems = x.declaredElements
            for (elem <- elems) {
              if (elem.name == name) {
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", elem.name)
              }
            }
          }
          case x: ScPatternDefinition => {
            val elems = x.declaredElements
            for (elem <- elems) {
              if (elem.name == name) {
                buf += ScalaBundle.message("introduced.variable.will.conflict.with.local", elem.name)
              }
            }
          }
          case _ =>
        }
        fromDoubles = fromDoubles.getPrevSibling
      }
      while (from != null) {
        buf ++= validateDown(from, name, allOcc)
        from = from.getNextSibling
      }
    }
    buf.toArray
  }

  def validateName(name: String, increaseNumber: Boolean): String = {
    if (noOccurrences) return name
    var res = name
    if (isOKImpl(res, false).length == 0) return res
    if (!increaseNumber) return ""
    var i = 1
    res = name + i
    while (!(isOKImpl(res, true).length == 0)) {
      i = i + 1
      res = name + i
    }
    res
  }
}