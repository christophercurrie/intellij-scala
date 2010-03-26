package org.jetbrains.plugins.scala.lang.resolve2

import com.intellij.testFramework.{ResolveTestCase, PsiTestCase}
import com.intellij.openapi.application.ex.PathManagerEx
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.openapi.vfs.VirtualFile
import scala.util.matching.Regex.{Match, MatchData}
import junit.framework._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScClass}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import java.lang.String
import com.intellij.psi.{PsiFile, PsiNamedElement, PsiReference, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveTestCase}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * Pavel.Fatin, 02.02.2010
 */

abstract class ResolveTestBase extends ScalaResolveTestCase {
  val pattern = """/\*\s*(.*?)\s*\*/\s*""".r
  type Parameters = Map[String, String]

  val Resolved = "resolved" // default: true
  val Name = "name" // default: reference name
  val File = "file" // default: this (if line or offset provided)
  val Line = "line"
  val Offset = "offset"
  val Length = "length"
  val Type = "type"
  val Path = "path"
  val Applicable = "applicable" // default: true
  val Accessible = "accessible" // default: true

  val Parameters = List(Resolved, Name, File, Line, Offset, Length, Type, Path, Applicable, Accessible)

  var options: List[Parameters] = List()
  var references: List[PsiReference] = List()


  override def setUp() {
    super.setUp
    options = List()
    references = List()
  }

  override def getTestDataPath: String = {
    return TestUtils.getTestDataPath + "/resolve2/"
  }

  override def configureByFileText(text: String, fileName: String, parentDir: VirtualFile): PsiReference = {
    myFile = if (parentDir == null) createFile(myModule, fileName, text) else createFile(myModule, parentDir, fileName, text)

    val matches = pattern.findAllIn(text).matchData

    for (m <- matches) {
      val parameters = parseParameters(m.group(1))
      val reference = myFile.findReferenceAt(m.end)

      assertKnown(parameters)
      Assert.assertNotNull("No reference found at offset " + m.end, references)

      options = parameters :: options
      references = reference :: references
    }

    options = options.reverse
    references = references.reverse
    
    Assert.assertFalse("At least one expectation must be specified", references.isEmpty)
    Assert.assertEquals("Options number", references.size, options.size)

    null
  }

  def assertKnown(parameters: Parameters) = {
    for ((key, value) <- parameters) {
      Assert.assertTrue("Unknown parameter: " + key + "\nAllowed: " + Parameters.mkString(", "),
        Parameters.contains(key))
    }
  }

  def parseParameters(s: String): Parameters = {
    if (s.isEmpty) Map() else Map(s.split("""\s*,\s*""").map(_.trim).map {
      it: String => val parts = it.split("""\s*:\s*"""); (parts(0), parts(1))
    }: _*)
  }

  def doTest {
    doTest(getTestName(false) + ".scala")
  }

  def doTest(file: String) {
    configureByFile(file)
    references.zip(options).foreach(it => doEachTest(it._1.asInstanceOf[ScReferenceElement], it._2))
  }

  def doEachTest(reference: ScReferenceElement, options: Parameters) {
    val referenceName = reference.refName
    val result = reference.advancedResolve
    val (target, accessible, applicable) = if(result.isDefined) (
            result.get.element,
            result.get.isAccessible,
            result.get.isApplicable) else (null, true, true)

    def message = format(myFile.getText, _: String, lineOf(reference))

    def assertEquals(name: String, v1: Any, v2: Any) {
      if(v1 != v2) Assert.fail(message(name + " - expected: " + v1 + ", actual: " + v2))
    }

    if (options.contains(Resolved) && options(Resolved) == "false") {
      Assert.assertNull(message(referenceName + " must NOT be resolved!"), target);
    } else {
      Assert.assertNotNull(message(referenceName + " must BE resolved!"), target);

      if (options.contains(Accessible) && options(Accessible) == "false") {
        Assert.assertFalse(message(referenceName + " must NOT be accessible!"), accessible);
      } else {
        Assert.assertTrue(message(referenceName + " must BE accessible!"), accessible);
      }

      if (options.contains(Applicable) && options(Applicable) == "false") {
        Assert.assertFalse(message(referenceName + " must NOT be applicable!"), applicable);
      } else {
        Assert.assertTrue(message(referenceName + " must BE applicable!"), applicable);
      }

      if (options.contains(Path)) {
        assertEquals(Path, options(Path), target.asInstanceOf[ScTypeDefinition].getQualifiedName)
      }

      if (options.contains(File) || options.contains(Offset) || options.contains(Line)) {
        val actual = target.getContainingFile.getVirtualFile.getNameWithoutExtension
        val expected = if (!options.contains(File) || options(File) == "this") {
          reference.getElement.getContainingFile.getVirtualFile.getNameWithoutExtension
        } else options(File)
        assertEquals(File, expected, actual)
      }

      val expectedName = if (options.contains(Name)) options(Name) else referenceName
      assertEquals(Name, expectedName, target.asInstanceOf[PsiNamedElement].getName)

      if (options.contains(Line)) {
        assertEquals(Line, options(Line).toInt, lineOf(target))
      }

      if (options.contains(Offset)) {
        assertEquals(Offset, options(Offset).toInt, target.getTextOffset)
      }

      if (options.contains(Length)) {
        assertEquals(Length, options(Length).toInt, target.getTextLength)
      }

      if (options.contains(Type)) {
        val expectedClass = Class.forName(options(Type))
        val targetClass = target.getClass
        val text = Type + " - expected: " + expectedClass.getSimpleName + ", actual: " + targetClass.getSimpleName
        Assert.assertTrue(message(text), expectedClass.isAssignableFrom(targetClass))
      }
    }
  }

  def lineOf(element: PsiElement) = {
    element.getContainingFile.getText.substring(0, element.getTextOffset).count(_ == '\n') + 1
  }

  def format(text: String, message: String, line: Int) = {
    val lines = text.lines.zipWithIndex.map(p => if (p._2 + 1 == line) p._1 + " // " + message else p._1)
    "\n\n" + lines.mkString("\n") + "\n"
  }
}
