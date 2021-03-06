package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiElementImpl
import api.expr._
import com.intellij.lang.ASTNode
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import api.base.ScConstructor
import api.toplevel.templates.ScTemplateBody
import types._
import result.{TypeResult, TypingContext, Failure, Success}
import api.ScalaElementVisitor
import com.intellij.psi.{PsiElementVisitor, PsiElement}

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScThisReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThisReference {
  override def toString: String = "ThisReference"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = refTemplate match {
    case Some(td) => ScThisReferenceImpl.getThisTypeForTypeDefinition(td, this)
    case _ => Failure("Cannot infer type", Some(this))
  }

  def refTemplate: Option[ScTemplateDefinition] = reference match {
    case Some(ref) => ref.resolve match {
      case td: ScTypeDefinition if PsiTreeUtil.isContextAncestor(td, ref, false) => Some(td)
      case _ => None
    }
    case None => {
      val encl = PsiTreeUtil.getContextOfType(this, false, classOf[ScTemplateBody])
      if (encl != null) Some(PsiTreeUtil.getContextOfType(encl, false, classOf[ScTemplateDefinition])) else None
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitThisReference(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitThisReference(this)
      case _ => super.accept(visitor)
    }
  }
}

object ScThisReferenceImpl {
  def getThisTypeForTypeDefinition(td: ScTemplateDefinition, expr: ScExpression): TypeResult[ScType] = {
    lazy val selfTypeOfClass: ScType = td.getTypeWithProjections(TypingContext.empty, thisProjections = true).map(tp => {
      td.selfType match {
        case Some(selfType) => Bounds.glb(tp, selfType)
        case _ => tp
      }
    }).getOrElse(return Failure("No clazz type found", Some(expr)))

    // SLS 6.5:  If the expression’s expected type is a stable type,
    // or C .this occurs as the prefix of a selection, its type is C.this.type,
    // otherwise it is the self type of class C .
    expr.getContext match {
      case r: ScReferenceExpression if r.qualifier.exists(_ == expr) =>
        Success(ScThisType(td), Some(expr))
      case _ => expr.expectedType() match {
        case Some(t) if t.isStable =>
          Success(ScThisType(td), Some(expr))
        case _ =>
          Success(selfTypeOfClass, Some(expr))
      }
    }
  }
}