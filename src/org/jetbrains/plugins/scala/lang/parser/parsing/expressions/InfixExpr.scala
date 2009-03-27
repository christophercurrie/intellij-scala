package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable.Stack
import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator

/**
 * @author AlexanderPodkhalyuzin
* Date: 03.03.2008
 */

/*
 * InfixExpr ::= PrefixExpr
 *             | InfixExpr id [nl] InfixExpr
 */

object InfixExpr {
  def parse(builder: PsiBuilder): Boolean = {
    val markerStack = new Stack[PsiBuilder.Marker]
    val opStack = new Stack[String]
    val infixMarker = builder.mark
    var backupMarker = builder.mark
    var count = 0
    if (!PrefixExpr.parse(builder)) {
      backupMarker.drop
      infixMarker.drop
      return false
    }
    var exitOf = true
    while (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && exitOf) {
      //need to know associativity
      val s = builder.getTokenText

      var exit = false
      while (!exit) {
        if (opStack.isEmpty) {
          opStack += s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
        else if (!compar(s, opStack.top, builder)) {
          opStack.pop
          backupMarker.drop
          backupMarker = markerStack.top.precede
          markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
        }
        else {
          opStack += s
          val newMarker = backupMarker.precede
          markerStack += newMarker
          exit = true
        }
      }
      val setMarker = builder.mark
      val opMarker = builder.mark
      builder.advanceLexer //Ate id
      opMarker.done(ScalaElementTypes.REFERENCE_EXPRESSION)
      builder.getTokenType match {
        case ScalaTokenTypes.tLINE_TERMINATOR => {
          if (!LineTerminator(builder.getTokenText)) {
            setMarker.rollbackTo
            count = 0
            backupMarker.drop
            exitOf = false
          } else {
            builder.advanceLexer //Ale nl
            backupMarker.drop
            backupMarker = builder.mark
            if (!PrefixExpr.parse(builder)) {
              count = 0
              setMarker.rollbackTo
              exitOf = false
            }
            else {
              count = count + 1
              setMarker.drop
            }
          }
        }
        case _ => {
          backupMarker.drop
          backupMarker = builder.mark
          if (!PrefixExpr.parse(builder)) {
            setMarker.rollbackTo
            count = 0
            exitOf = false
          }
          else {
            setMarker.drop
            count = count + 1
          }
        }
      }
    }
    if (exitOf) backupMarker.drop
    if (count > 0) {
      while (count > 0 && !markerStack.isEmpty) {
        markerStack.pop.done(ScalaElementTypes.INFIX_EXPR)
        count -= 1
      }

    }
    infixMarker.drop
    while (!markerStack.isEmpty) {
      markerStack.pop.drop
    }
    return true
  }
  //private var assoc: Int = 0  //this mark associativity: left - 1, right - -1
  //Defines priority
  private def priority(id: String): Int = {
    id.charAt(0) match {
      case '~' | '#' | '@' | '$' | '?' | '\\' => 0
      case '*' | '/' | '%' => 1
      case '+' | '-' => 2
      case ':' => 3
      case '=' | '!' => 4
      case '<' | '>' => 5
      case '&' => 6
      case '^' => 7
      case '|' => 8
      case _ => 9
    }
  }
  //compares two operators a id2 b id1 c
  private def compar(id1: String, id2: String, builder: PsiBuilder): Boolean = {
    if (priority(id1) < priority(id2)) return true //  a * b + c  =((a * b) + c)
    else if (priority(id1) > priority(id2)) return false //  a + b * c = (a + (b * c))
    else if (associate(id1) == associate(id2))
      if (associate(id1) == -1) return true
      else return false
    else {
      builder error ErrMsg("wrong.type.associativity")
      return false
    }
  }
  private def opeq(id1: String, id2: String): Boolean = priority(id1) == priority(id2)
  //Associations of operator
  private def associate(id: String): Int = {
    id.charAt(id.length - 1) match {
      case ':' => return -1
      // right
      case _ => return +1 // left
    }
  }
}