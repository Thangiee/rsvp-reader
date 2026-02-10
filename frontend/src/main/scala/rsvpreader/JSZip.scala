package rsvpreader

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.ArrayBuffer

@js.native
@JSGlobal("JSZip")
class JSZip() extends js.Object:
  def loadAsync(data: ArrayBuffer): js.Promise[JSZip] = js.native
  def file(name: String): JSZipObject = js.native
  def files: js.Dictionary[JSZipObject] = js.native

@js.native
trait JSZipObject extends js.Object:
  def async(tpe: String): js.Promise[Any] = js.native
  val name: String = js.native
  val dir: Boolean = js.native
