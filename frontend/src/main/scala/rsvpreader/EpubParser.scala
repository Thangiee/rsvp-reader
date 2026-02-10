package rsvpreader

import scala.scalajs.js
import org.scalajs.dom
import kyo.*
import rsvpreader.book.*

object EpubParser:

  /** Parse an EPUB file using @lingo-reader/epub-parser.
    * Accepts a browser File object (from file input).
    */
  def parse(file: dom.File): js.Promise[Book] =
    LingoEpubParser.initEpubFile(file).`then`[Book] { epub =>
      val metadata = epub.getMetadata()
      val title = metadata.title
      val author = metadata.creator
        .toOption
        .flatMap(_.headOption)
        .map(_.contributor)
        .getOrElse("")

      val spine = epub.getSpine()

      // Load all chapters in spine order
      val chapterPromises = spine.map { item =>
        epub.loadChapter(item.id).`then`[Chapter] { processed =>
          val text = extractText(processed.html)
          val chTitle = extractChapterTitle(processed.html, item.href)
          Chapter(chTitle, text)
        }
      }

      js.Promise.all(js.Array(chapterPromises.toSeq*)).`then`[Book] { chaptersJs =>
        val chapters = chaptersJs.toArray
          .map(_.asInstanceOf[Chapter])
          .filter(_.text.trim.nonEmpty)
        epub.destroy()
        Book(title, author, Span.from(chapters))
      }
    }

  /** Extract plain text from chapter HTML string.
    * Uses browser DOMParser to parse the HTML and extract text blocks.
    */
  private def extractText(html: String): String =
    val parser = new dom.DOMParser()
    val doc = parser.parseFromString(html, dom.MIMEType.`text/html`)
    val body = doc.querySelector("body")
    if body == null then ""
    else
      val blocks = body.querySelectorAll("p, h1, h2, h3, h4, h5, h6, li, blockquote, div")
      if blocks.length == 0 then body.textContent.trim
      else
        (0 until blocks.length)
          .map(i => blocks(i).textContent.trim)
          .filter(_.nonEmpty)
          .mkString("\n\n")

  /** Extract chapter title from HTML heading elements. */
  private def extractChapterTitle(html: String, fallback: String): String =
    val parser = new dom.DOMParser()
    val doc = parser.parseFromString(html, dom.MIMEType.`text/html`)
    val heading = doc.querySelector("h1, h2, h3, title")
    if heading != null && heading.textContent.trim.nonEmpty then heading.textContent.trim
    else
      val name = fallback.split('/').last.replaceAll("\\.[^.]+$", "")
      name
