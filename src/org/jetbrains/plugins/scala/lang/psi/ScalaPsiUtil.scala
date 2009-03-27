package org.jetbrains.plugins.scala.lang.psi

import annotations.NotNull
import api.base.patterns.ScCaseClause
import api.base.types.ScTypeElement
import api.expr.{ScGenericCall, ScAnnotation, ScExpression}
import api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import api.toplevel.typedef.ScObject
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.base.{ScConstructor, ScStableCodeReferenceElement, ScModifierList}
import api.statements._
import api.toplevel.templates.ScTemplateBody
import api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.{CachedValueProvider, CachedValue, PsiFormatUtil}
import lang.psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import structureView.ScalaElementPresentation
/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaPsiUtil {
  def genericCallSubstitutor(tp: Seq[String], gen: ScGenericCall): ScSubstitutor = {
    val typeArgs: Seq[ScTypeElement] = gen.typeArgs.typeArgs
    val map = new collection.mutable.HashMap[String, ScType]
    for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
      map += Tuple(tp(i), typeArgs(i).calcType)
    }
    new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
  }

  def namedElementSig(x: PsiNamedElement): Signature = new Signature(x.getName, Seq.empty, 0, Array[PsiTypeParameter](), ScSubstitutor.empty)

  def superValsSignatures(x: PsiNamedElement): Seq[FullSignature] = {
    val empty = Seq.empty
    val typed = x match {case x: ScTyped => x case _ => return empty}
    val context: PsiElement = nameContext(typed) match {
      case value: ScValue if value.getParent.isInstanceOf[ScTemplateBody] => value
      case value: ScVariable if value.getParent.isInstanceOf[ScTemplateBody] => value
      case _ => return empty
    }
    val clazz = context.asInstanceOf[PsiMember].getContainingClass
    val s = new FullSignature(namedElementSig(x), typed.calcType,
      x.asInstanceOf[NavigatablePsiElement], clazz)
    val t = TypeDefinitionMembers.getSignatures(clazz).get(s) match {
      //partial match
      case Some(x) => x.supers.map{_.info}
    }
    return t
  }

  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias | _: ScParameter | _: PsiMethod | _: ScCaseClause => true
        case _ => false
      }
    }
    if (isAppropriatePsiElement(x)) return x
    while (parent != null && !isAppropriatePsiElement(parent)) parent = parent.getParent
    return parent
  }

  def adjustTypes(element: PsiElement): Unit = {
    for (child <- element.getChildren) {
      child match {
        case x: ScStableCodeReferenceElement => x.resolve match {
          case clazz: PsiClass =>
            x.replace(ScalaPsiElementFactory.createReferenceFromText(clazz.getName, clazz.getManager)).
                asInstanceOf[ScStableCodeReferenceElement].bindToElement(clazz)
          case _ =>
        }
        case _ => adjustTypes(child)
      }
    }
  }

  def getMethodPresentableText(method: PsiMethod): String = {
    val buffer = new StringBuffer("")
    method match {
      case method: ScFunction => {
        return ScalaElementPresentation.getMethodPresentableText(method, false)
      }
      case _ => {
        val PARAM_OPTIONS: Int = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER
        return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
          PARAM_OPTIONS | PsiFormatUtil.SHOW_PARAMETERS, PARAM_OPTIONS)
      }
    }
  }

  def getModifiersPresentableText(modifiers: ScModifierList): String = {
    if (modifiers == null) return ""
    val buffer = new StringBuilder("")
    for (modifier <- modifiers.getNode.getChildren(null) if !isLineTerminator(modifier.getPsi)) buffer.append(modifier.getText + " ")
    return buffer.toString
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => return true
      case _ =>
    }
    return element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
  }

  def getApplyMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
              if n.method.getName == "apply" &&
                    (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) yield n).toSeq
  }

  def getUpdateMethods(clazz: PsiClass): Seq[PhysicalSignature] = {
    (for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
              if n.method.getName == "update" &&
                    (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) yield n).toSeq
  }

  /**
   * This method try to conform given expression to method's first parameter clause.
   * @return all methods which can by applied to given expressions
   */
  def getMethodsConforsToMethodCall(methods: Seq[PhysicalSignature], params: Seq[ScExpression], subst: PsiMethod => ScSubstitutor): Seq[PhysicalSignature] = {
    def check(sign: PhysicalSignature): Boolean = {
      val meth = sign.method
      meth match {
        case fun: ScFunction => {
          val clauses: Seq[ScParameterClause] = fun.paramClauses.clauses
          if (clauses.length == 0) {
            if (params.length == 0) return true
            else return false
          } else {
            val clause: ScParameterClause = clauses.apply(0)
            val methodParams: Seq[ScParameter] = clause.parameters
            val length = methodParams.length
            if (length == 0) {
              if (params.length == 0) return true
              else return false
            }
            //so method have not zero params
            //length sould be equal or last parameter should be repeated
            if (!(length == params.length ||
                    (length < params.length && methodParams(length - 1).isRepeatedParameter))) return false
            for (i <- 0 to params.length - 1) {
              val parameter: ScParameter = methodParams(Math.min(i, length -1))
              val typez: ScType = parameter.typeElement match {
                case Some(te) => subst(meth).subst(te.calcType)
                case None => types.Any
              }
              if (!(params(i).getType: ScType).conforms(typez)) return false
            }
            return true
          }
        }
        case meth: PsiMethod => {
          val methodParams = meth.getParameterList.getParameters
          val length: Int = methodParams.length
          if (length == 0) {
            if (methodParams.length == 0) return true
            else return false
          }
          //so method have not zero params
          //length sould be equal or last parameter should be repeated
          if (!(length == params.length || (
                  length < params.length && methodParams.apply(length - 1).isVarArgs
                  ))) return false
          for (i <- 0 to params.length - 1) {
            val parameter: PsiParameter = methodParams(Math.min(i, length - 1))
            val typez: ScType = subst(meth).subst(ScType.create(parameter.getType, meth.getProject))
            if (!(params(i).getType: ScType).conforms(typez)) return false
          }
          return true
        }
      }
    }
    for (method <- methods if check(method)) yield method
  }

  /**
   * For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, classes: Class[_ <: PsiElement]*): PsiElement = {
    getParentOfType(element, false, classes: _*)
  }

  /**
   * For one classOf use PsiTreeUtil.getParenteOfType instead
   */
  def getParentOfType(element: PsiElement, strict: Boolean, classes: Class[_ <: PsiElement]*): PsiElement = {
    var el: PsiElement = if (!strict) element else {
      if (element == null) return null
      element.getParent
    }
    while (el != null && classes.find(_.isInstance(el)) == None) el = el.getParent
    return el
  }
}