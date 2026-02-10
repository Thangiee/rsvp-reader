package rsvpreader

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom

/** ScalaJS facade for @lingo-reader/epub-parser, exposed as window.__epubParser global. */

@js.native
@JSGlobal("__epubParser")
object LingoEpubParser extends js.Object:
  def initEpubFile(file: dom.File): js.Promise[EpubFile] = js.native

@js.native
trait EpubFile extends js.Object:
  def getMetadata(): EpubMetadata = js.native
  def getSpine(): js.Array[SpineItem] = js.native
  def loadChapter(id: String): js.Promise[EpubProcessedChapter] = js.native
  def destroy(): Unit = js.native

@js.native
trait EpubMetadata extends js.Object:
  val title: String = js.native
  val creator: js.UndefOr[js.Array[EpubContributor]] = js.native

@js.native
trait EpubContributor extends js.Object:
  val contributor: String = js.native

@js.native
trait SpineItem extends js.Object:
  val id: String = js.native
  val href: String = js.native
  val mediaType: String = js.native

@js.native
trait EpubProcessedChapter extends js.Object:
  val html: String = js.native
