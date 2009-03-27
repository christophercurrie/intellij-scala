package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import api.toplevel.typedef.ScObject
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl

/**
 * @author ilyas
 */

class ScObjectDefinitionElementType extends ScTypeDefinitionElementType[ScObject]("object definition") {

  def createElement(node: ASTNode): PsiElement = new ScObjectImpl(node)

  def createPsi(stub: ScTypeDefinitionStub) = new ScObjectImpl(stub)

}