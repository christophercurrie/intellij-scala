package org.jetbrains.plugins.scala.lang.refactoring.mock

import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.{RangeMarker, LogicalPosition, SelectionModel}
import java.lang.String
import com.intellij.openapi.editor.VisualPosition

/**
 * Pavel Fatin
 */

class SelectionModelStub extends SelectionModel {
  def getTextAttributes: TextAttributes = null

  def getBlockSelectionGuard: RangeMarker = null

  def isBlockSelectionGuarded: Boolean = false

  def getBlockEnd: LogicalPosition = null

  def getBlockStart: LogicalPosition = null

  def getBlockSelectionEnds: Array[Int] = null

  def getBlockSelectionStarts: Array[Int] = null

  def hasBlockSelection: Boolean = false

  def removeBlockSelection: Unit = {}

  def setBlockSelection(blockStart: LogicalPosition, blockEnd: LogicalPosition): Unit = {}

  def copySelectionToClipboard: Unit = {}

  def selectWordAtCaret(honorCamelWordsSettings: Boolean): Unit = {}

  def selectLineAtCaret: Unit = {}

  def removeSelectionListener(listener: SelectionListener): Unit = {}

  def addSelectionListener(listener: SelectionListener): Unit = {}

  def removeSelection: Unit = {}

  def setSelection(startOffset: Int, endOffset: Int): Unit = {}

  def setSelection(startOffset: Int, endPosition: VisualPosition, endOffset: Int): Unit = {}

  def setSelection(startPosition: VisualPosition, startOffset: Int, endPosition: VisualPosition, endOffset: Int): Unit = {}

  def hasSelection: Boolean = false

  def getLeadSelectionOffset: Int = 0

  def getLeadSelectionPosition: VisualPosition = null

  def getSelectionStartPosition: VisualPosition = null

  def getSelectionEndPosition: VisualPosition = null

  def getSelectedText: String = ""

  def getSelectionEnd: Int = 0

  def getSelectionStart: Int = 0
}