package org.jetbrains.plugins.scala.lang.parser.parsing.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._

  object Expr {
  /*
  Common expression
  Default grammar
  Expr ::= Bindings �=>� Expr
          | Expr1               (a)
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        val exprMarker = builder.mark()

        var result = CompositeExpr.parse(builder)
        /** Case (a) **/
        if (!result.equals(ScalaElementTypes.WRONGWAY)) {
          if (result.equals(ScalaElementTypes.LITERAL) ||
              result.equals(ScalaElementTypes.POSTFIX_EXPR)){
            exprMarker.done(ScalaElementTypes.EXPR)
          } else exprMarker.drop()
          ScalaElementTypes.EXPR
        }
        else {
          exprMarker.rollbackTo()
          ScalaElementTypes.WRONGWAY
        }
      }


  }

    object Exprs {
  /*
  Expression list
  Default grammar
  Exprs ::= Expr {�,� Expr}
  */

    def parse(builder : PsiBuilder) : ScalaElementType = {
        val exprsMarker = builder.mark()

        def subParse: ScalaElementType = {
          ParserUtils.rollForward(builder)
          builder getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
              ParserUtils.rollForward(builder)
              val res1 = Expr.parse(builder)
              if (res1.equals(ScalaElementTypes.EXPR)) {
                subParse
              } else {
                builder.error("Argument expected")
                //exprsMarker.done(ScalaElementTypes.EXPRS)
                exprsMarker.drop
                ScalaElementTypes.EXPRS
              }
            }
            case _ => {
              //exprsMarker.done(ScalaElementTypes.EXPRS)
              exprsMarker.drop
              ScalaElementTypes.EXPRS
            }
          }
        }

        var result = Expr.parse(builder)
        /** Case (a) **/
        if (result.equals(ScalaElementTypes.EXPR)) {
          subParse
        }
        else {
          builder.error("Argument expected")
          //exprsMarker.done(ScalaElementTypes.EXPRS)
          exprsMarker.drop
          ScalaElementTypes.EXPRS
        //  exprsMarker.rollbackTo()
        //  ScalaElementTypes.WRONGWAY
        }
      }
  }



}