package rsvpreader.book

import kyo.*
import munit.FunSuite

class BookSuite extends FunSuite:

  test("Book.fromPlainText derives title from first ~30 chars at word boundary"):
    val text = "The quick brown fox jumped over the lazy dog near the river"
    val book = Book.fromPlainText(text)
    assertEquals(book.title, "The quick brown fox jumped...")
    assertEquals(book.author, "")
    assertEquals(book.chapters.length, 1)
    assertEquals(book.chapters(0).title, "")
    assertEquals(book.chapters(0).text, text)

  test("Book.fromPlainText with short text uses full text as title"):
    val text = "Hello world"
    val book = Book.fromPlainText(text)
    assertEquals(book.title, "Hello world")

  test("Book.fromPlainText with empty text"):
    val book = Book.fromPlainText("")
    assertEquals(book.title, "")
    assertEquals(book.chapters.length, 1)
    assertEquals(book.chapters(0).text, "")

  test("Book.fromPlainText trims leading/trailing whitespace from title"):
    val text = "  Hello world  "
    val book = Book.fromPlainText(text)
    assertEquals(book.title, "Hello world")
    // text itself is preserved as-is
    assertEquals(book.chapters(0).text, text)

  test("Chapter text is preserved exactly"):
    val chapter = Chapter("Ch 1", "Some text here.")
    assertEquals(chapter.text, "Some text here.")

  test("Book with multiple chapters"):
    val book = Book("My Book", "Author", Span(
      Chapter("Chapter 1", "First chapter text."),
      Chapter("Chapter 2", "Second chapter text.")
    ))
    assertEquals(book.chapters.length, 2)
    assertEquals(book.chapters(1).title, "Chapter 2")
