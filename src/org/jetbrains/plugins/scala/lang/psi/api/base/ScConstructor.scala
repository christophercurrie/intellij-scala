package org.jetbrains.plugins.scala.lang.psi.api.base

import expr.ScArgumentExprList
import psi.ScalaPsiElement
import statements.params.ScArguments
import statements.ScFunction
import types.{ScSimpleTypeElement, ScTypeElement}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement = findChildByClass(classOf[ScTypeElement])

  def args = findChildByClass(classOf[ScArgumentExprList])
  def arguments = findChildrenByClass(classOf[ScArgumentExprList])
}