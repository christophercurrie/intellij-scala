package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable


import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColors}
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Editor, VisualPosition}
import com.intellij.openapi.wm.WindowManager
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.RefactoringMessageDialog
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import namesSuggester.NameSuggester
import psi.api.ScalaFile
import psi.ScalaPsiUtil
import psi.types.{ScType, ScFunctionType}
import psi.api.expr._
import psi.api.statements.ScFunction
import psi.api.statements.ScValue
import psi.api.statements.ScVariable
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.toplevel.typedef.ScTrait
import psi.api.toplevel.typedef.ScClass
import psi.api.base.ScReferenceElement
import collection.mutable.ArrayBuffer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project
import psi.api.toplevel.typedef.ScMember
import scala.util.ScalaUtils
import refactoring.util.ScalaRefactoringUtil

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

class ScalaIntroduceVariableHandler extends RefactoringActionHandler {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")
  var deleteOccurence = false;



  private def getLineText(editor: Editor): String = {
    val lineNumber = editor.getCaretModel.getLogicalPosition.line
    if (lineNumber >= editor.getDocument.getLineCount) return ""
    val caret = editor.getCaretModel.getVisualPosition
    val lineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line, 0));
    val nextLineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0))
    val start = editor.logicalPositionToOffset(lineStart)
    val end = editor.logicalPositionToOffset(nextLineStart)
    return editor.getDocument.getText.substring(start, end)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    if (!editor.getSelectionModel.hasSelection) editor.getSelectionModel.selectLineAtCaret
    
    val lineText = getLineText(editor)
    if (editor.getSelectionModel.getSelectedText != null &&
            lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim) deleteOccurence = true
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
    invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments
      if (!file.isInstanceOf[ScalaFile])
        showErrorMessage(ScalaBundle.message("only.for.scala"), project)

      if (!ScalaRefactoringUtil.ensureFileWritable(project, file))
        showErrorMessage(ScalaBundle.message("file.is.not.writable"), project)

      val expr: ScExpression = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project))

      if (expr.isInstanceOf[ScConstrExpr]) showErrorMessage(ScalaBundle.message("cannot.refactor.constr.expression"), project)

      val typez: ScType = expr.getType match {
        case ScFunctionType(ret, params) if params.length == 0 => ret
        case x => x
      }
      var parent: PsiElement = ScalaPsiUtil.getParentOfType(expr, classOf[ScalaFile], classOf[ScGuard])
      if (parent.isInstanceOf[ScGuard]) showErrorMessage(ScalaBundle.message("refactoring.is.not.supported.in.guard"), project)

      val enclosingContainer: PsiElement = ScalaRefactoringUtil.getEnclosingContainer(expr)
      if (enclosingContainer == null) showErrorMessage(ScalaBundle.message("wrong.refactoring.context"), project)

      val occurrences: Array[ScExpression] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), enclosingContainer)
      // Getting settings
      var validator = new ScalaVariableValidator(this, project, expr, occurrences, enclosingContainer)
      var dialog = getDialog(project, editor, expr, typez, occurrences, false, validator)
      if (!dialog.isOK) return

      val varName: String = dialog.getEnteredName
      var varType: ScType = dialog.getSelectedType
      val isVariable: Boolean = dialog.isDeclareVariable
      val replaceAllOccurrences: Boolean = dialog.isReplaceAllOccurrences
      runRefactoring(expr, editor, enclosingContainer, occurrences, varName, varType, replaceAllOccurrences, isVariable)
    }
    catch {
      case _: IntroduceException => return
    }
  }

  def runRefactoring(selectedExpr: ScExpression, editor: Editor, tempContainer: PsiElement,
                    occurrences_ : Array[ScExpression], varName: String, varType: ScType,
                    replaceAllOccurrences: Boolean, isVariable: Boolean) {
    var offset = -1
    val runnable = new Runnable() {
      def run() {
        val occurrences: Array[ScExpression] = if (!replaceAllOccurrences) {
          Array[ScExpression](selectedExpr)
        } else occurrences_
        var mark = 0
        for (i <- 0 to occurrences.length - 1 if occurrences(i) == selectedExpr) mark = i
        var parent: PsiElement = occurrences(0);
        if (parent != tempContainer)
          while (parent.getParent() != tempContainer) parent = parent.getParent
        def getReferencesTo(binding: PsiElement, element: PsiElement): Array[PsiElement] = {
          val buf = new ArrayBuffer[PsiElement]
          element match {
            case x: ScReferenceElement if (x.resolve == binding) => buf += element
            case _ => for (child <- element.getChildren) buf ++= getReferencesTo(binding, child)
          }
          return buf.toArray
        }
        tempContainer match {
          case x: ScBlock => {
            var ref: PsiElement = null
            var cl = tempContainer
            while (cl != null && !cl.isInstanceOf[ScClass] && !cl.isInstanceOf[ScTrait]) cl = cl.getParent
            if (cl != null) {
              cl match {
                case x: ScTypeDefinition => {
                  for (member <- x.members) {
                    member match {
                      case x: ScVariable => for (el <- x.declaredElements if el.name == varName) ref = el
                      case x: ScValue => for (el <- x.declaredElements if el.name == varName) ref = el
                      case _ =>
                    }
                  }
                  for (function <- x.functions) {
                    function match {
                      case x: ScFunction if x.name == varName && x.parameters.size == 0 => ref = x
                      case _ =>
                    }
                  }
                }
              }
            }
            if (ref != null) {
              for (el <- getReferencesTo(ref, tempContainer)) {
                if (occurrences.contains(el)) {
                  for (i <- 1 to occurrences.size - 1 if occurrences(i) == el)
                    occurrences(i) = el.asInstanceOf[ScExpression].replaceExpression(ScalaPsiElementFactory.createExpressionFromText("this." + el.getText, el.getManager), false)
                } else
                  el.asInstanceOf[ScExpression].replaceExpression(ScalaPsiElementFactory.createExpressionFromText("this." + el.getText, el.getManager), false)
              }
            }
            val varDecl = ScalaPsiElementFactory.createDeclaration(varType, varName,
              isVariable, ScalaRefactoringUtil.getExprFrom(occurrences(0)), selectedExpr.getManager)
            x.addDefinition(varDecl, parent)
            val declType = varDecl match {case v: ScVariable => v.typeElement case v: ScValue => v.typeElement}
            declType match {case Some(declType) => ScalaPsiUtil.adjustTypes(declType) case None =>}
            if (!deleteOccurence || replaceAllOccurrences) {
              for (i <- 0 to occurrences.length - 1; occurrence = occurrences(i)) {
                if (occurrence.isInstanceOf[ScBlockExpr] && occurrence.getParent.isInstanceOf[ScArgumentExprList]) {
                  val newExpr: ScExpression = occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("(" + varName + ")", occurrence.getManager), true)
                  if (i == mark) offset = newExpr.getTextRange.getEndOffset
                }
                else {
                  val flag = ScalaRefactoringUtil.hasNltoken(occurrence)
                  val newExpr: ScExpression = occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText(varName, occurrence.getManager), true)
                  if (flag) {
                    if (newExpr.getNextSibling != null) newExpr.getNode.getTreeParent.addChild(ScalaPsiElementFactory.createNewLineNode(newExpr.getManager), newExpr.getNextSibling.getNode)
                    else newExpr.getNode.getTreeParent.addChild(ScalaPsiElementFactory.createNewLineNode(newExpr.getManager))
                  }
                  if (i == mark) offset = newExpr.getTextRange.getEndOffset + 1
                }
              }
            } else {
              for (occurrence <- occurrences) {
                val parent = occurrence.getParent.getNode
                val prev = occurrence.getNode.getTreePrev
                offset = occurrence.getTextRange.getStartOffset
                val flag = ScalaRefactoringUtil.hasNltoken(occurrence)
                parent.removeChild(occurrence.getNode)
                if (!flag) parent.removeChild(prev)
              }
            }
          }
          case x: ScExpression => {
            var ref: PsiElement = null
            var cl = tempContainer
            while (cl != null && !cl.isInstanceOf[ScClass] && !cl.isInstanceOf[ScTrait]) cl = cl.getParent
            if (cl != null) {
              cl match {
                case x: ScTypeDefinition => {
                  for (member <- x.members) {
                    member match {
                      case x: ScVariable => for (el <- x.declaredElements if el.name == varName) ref = el
                      case x: ScValue => for (el <- x.declaredElements if el.name == varName) ref = el
                      case _ =>
                    }
                  }
                  for (function <- x.functions) {
                    function match {
                      case x: ScFunction if x.name == varName && x.parameters.size == 0 => ref = x
                      case _ =>
                    }
                  }
                }
              }
            }
            if (ref != null) {
              for (el <- getReferencesTo(ref, tempContainer)) {
                if (occurrences.contains(el)) {
                  for (i <- 1 to occurrences.size - 1 if occurrences(i) == el)
                    occurrences(i) = el.asInstanceOf[ScExpression].replaceExpression(ScalaPsiElementFactory.createExpressionFromText("this." + el.getText, el.getManager), false)
                } else
                  el.asInstanceOf[ScExpression].replaceExpression(ScalaPsiElementFactory.createExpressionFromText("this." + el.getText, el.getManager), false)
              }
            }
            val varDecl = ScalaPsiElementFactory.createDeclaration(varType, varName,
              isVariable, ScalaRefactoringUtil.getExprFrom(occurrences(0)), selectedExpr.getManager)
            var container = x
            if (!deleteOccurence || replaceAllOccurrences) {
              for (occurrence <- occurrences) {
                if (occurrence == container)
                  container = if (occurrence.isInstanceOf[ScBlockExpr] && occurrence.getParent.isInstanceOf[ScArgumentExprList])
                                occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("(" + varName + ")", occurrence.getManager), true)
                              else
                                occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText(varName, occurrence.getManager), true)
                else if (occurrence.isInstanceOf[ScBlockExpr] && occurrence.getParent.isInstanceOf[ScArgumentExprList])
                  occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("(" + varName + ")", occurrence.getManager), true)
                else {
                  val flag = ScalaRefactoringUtil.hasNltoken(occurrence)
                  val newExpr: ScExpression = occurrence.replaceExpression(ScalaPsiElementFactory.createExpressionFromText(varName, occurrence.getManager), true)
                  if (flag) {
                    if (newExpr.getNextSibling != null) newExpr.getNode.getTreeParent.addChild(ScalaPsiElementFactory.createNewLineNode(newExpr.getManager), newExpr.getNextSibling.getNode)
                    else newExpr.getNode.getTreeParent.addChild(ScalaPsiElementFactory.createNewLineNode(newExpr.getManager))
                  }
                }
              }
            } else {
              for (occurrence <- occurrences) {
                val parent = occurrence.getParent.getNode
                val prev = occurrence.getNode.getTreePrev
                offset = occurrence.getTextRange.getStartOffset
                val flag = ScalaRefactoringUtil.hasNltoken(occurrence)
                parent.removeChild(occurrence.getNode)
                if (!flag) parent.removeChild(prev)
              }
            }
            val block: ScBlock = container.replaceExpression(ScalaPsiElementFactory.createBlockFromExpr(container, container.getManager), false).asInstanceOf[ScBlock]
            block.addDefinition(varDecl, block.getFirstChild.getNextSibling.getNextSibling);
            val declType = varDecl match {case v: ScVariable => v.typeElement case v: ScValue => v.typeElement}
            declType match {case Some(declType) => ScalaPsiUtil.adjustTypes(declType) case None =>}
          }
          case x: ScEnumerators => {
            //todo: 
          }
          case _ => {
            showErrorMessage(ScalaBundle.message("operation.not.supported.in.current.block"), editor.getProject)
            return
          }
        }
        if (offset != -1) editor.getCaretModel.moveToOffset(offset - 1)
      }
    }

    ScalaUtils.runWriteAction(runnable, editor.getProject, REFACTORING_NAME);
    editor.getSelectionModel.removeSelection
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: ScType, occurrences: Array[ScExpression],
                          declareVariable: Boolean, validator: ScalaVariableValidator): ScalaIntroduceVariableDialog = {
    // Add occurences highlighting
    val highlighters = new java.util.ArrayList[RangeHighlighter]
    var highlightManager: HighlightManager = null
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      val colorsManager = EditorColorsManager.getInstance
      val attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences.asInstanceOf[Array[PsiElement]], attributes, true, highlighters)
      }
    }

    val possibleNames = NameSuggester.suggestNames(expr, validator)
    val dialog = new ScalaIntroduceVariableDialog(project, typez, occurrences.length, validator, possibleNames)
    dialog.show
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    } else {
      if (editor != null) {
        import _root_.scala.collection.jcl.Conversions._

        for (highlighter <- highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog
  }

  def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
    val conflictsDialog = new ConflictsDialog(project, conflicts: _*)
    conflictsDialog.show
    return conflictsDialog.isOK
  }

  /**
   * @throws IntroduceException
   */
  def showErrorMessage(text: String, project: Project): Nothing = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    val dialog = new RefactoringMessageDialog("Introduce variable refactoring", text,
            HelpID.INTRODUCE_VARIABLE, "OptionPane.errorIcon", false, project)
    dialog.show
    throw new IntroduceException
  }

  private class IntroduceException extends Exception
}