package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamClauseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeParamClause {

  override def toString: String = "TypeParameterClause"

  def typeParameters() : Seq[ScTypeParam] = findChildrenByClass(classOf[ScTypeParam])
}