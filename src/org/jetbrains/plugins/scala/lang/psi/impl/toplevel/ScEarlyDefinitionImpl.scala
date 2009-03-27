package org.jetbrains.plugins.scala.lang.psi.impl.toplevel

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScEarlyDefinitionsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEarlyDefinitions {
  override def toString: String = "EarlyDefinitions"
}