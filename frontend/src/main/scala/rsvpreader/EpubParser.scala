package rsvpreader

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom
import kyo.*
import rsvpreader.book.*

object EpubParser:

  def parse(data: ArrayBuffer): js.Promise[Book] =
    val zip = new JSZip()
    zip.loadAsync(data).`then`[Book] { loaded =>
      // 1. Read container.xml to find OPF path
      val containerFile = loaded.file("META-INF/container.xml")
      if containerFile == null then throw new Exception("Invalid EPUB: missing META-INF/container.xml")
      containerFile.async("string").`then`[Book] { containerXml =>
        val parser = new dom.DOMParser()
        val containerDoc = parser.parseFromString(containerXml.asInstanceOf[String], dom.MIMEType.`text/xml`)
        val rootfileEl = containerDoc.querySelector("rootfile")
        if rootfileEl == null then throw new Exception("Invalid EPUB: no rootfile in container.xml")
        val opfPath = rootfileEl.getAttribute("full-path")
        val opfDir = opfPath.lastIndexOf('/') match
          case -1 => ""
          case i  => opfPath.substring(0, i + 1)

        // 2. Read OPF file
        val opfFile = loaded.file(opfPath)
        if opfFile == null then throw new Exception(s"Invalid EPUB: missing OPF file at $opfPath")
        opfFile.async("string").`then`[Book] { opfXml =>
          val opfDoc = parser.parseFromString(opfXml.asInstanceOf[String], dom.MIMEType.`text/xml`)

          // Extract metadata
          val titleEl = opfDoc.querySelector("metadata title")
          val title = if titleEl != null then titleEl.textContent.trim else "Untitled"
          val creatorEl = opfDoc.querySelector("metadata creator")
          val author = if creatorEl != null then creatorEl.textContent.trim else ""

          // 3. Get spine order (list of itemref idref values)
          val spineItems = opfDoc.querySelectorAll("spine itemref")
          val idrefs = (0 until spineItems.length).map(i =>
            spineItems(i).asInstanceOf[dom.Element].getAttribute("idref")
          )

          // 4. Map idrefs to href via manifest
          val manifestItems = opfDoc.querySelectorAll("manifest item")
          val idToHref = scala.collection.mutable.Map[String, String]()
          (0 until manifestItems.length).foreach { i =>
            val el = manifestItems(i).asInstanceOf[dom.Element]
            idToHref(el.getAttribute("id")) = el.getAttribute("href")
          }

          val chapterHrefs = idrefs.flatMap(id => idToHref.get(id))

          // 5. Read each chapter XHTML and extract text
          val chapterPromises = chapterHrefs.map { href =>
            val fullPath = opfDir + href
            val chFile = loaded.file(fullPath)
            if chFile == null then js.Promise.resolve[Chapter](Chapter(href, ""))
            else chFile.async("string").`then`[Chapter] { html =>
              val chDoc = parser.parseFromString(html.asInstanceOf[String], dom.MIMEType.`text/html`)
              val text = extractText(chDoc)
              val chTitle = extractChapterTitle(chDoc, href)
              Chapter(chTitle, text)
            }
          }

          js.Promise.all(js.Array(chapterPromises*)).`then`[Book] { chaptersJs =>
            val chapters = chaptersJs.toArray.map(_.asInstanceOf[Chapter]).filter(_.text.trim.nonEmpty)
            Book(title, author, Span.from(chapters))
          }
        }
      }
    }

  private def extractText(doc: dom.Document): String =
    val body = doc.querySelector("body")
    if body == null then ""
    else
      // Get all paragraph-level elements and join with double newlines
      val blocks = body.querySelectorAll("p, h1, h2, h3, h4, h5, h6, li, blockquote, div")
      if blocks.length == 0 then body.textContent.trim
      else
        (0 until blocks.length)
          .map(i => blocks(i).textContent.trim)
          .filter(_.nonEmpty)
          .mkString("\n\n")

  private def extractChapterTitle(doc: dom.Document, fallback: String): String =
    val heading = doc.querySelector("h1, h2, h3, title")
    if heading != null && heading.textContent.trim.nonEmpty then heading.textContent.trim
    else
      // Strip path and extension from href as fallback
      val name = fallback.split('/').last.replaceAll("\\.[^.]+$", "")
      name
