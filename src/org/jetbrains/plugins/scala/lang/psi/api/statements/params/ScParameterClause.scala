package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]
  def isImplicit: Boolean = getNode.findChildByType(ScalaTokenTypes.kIMPLICIT) != null

}