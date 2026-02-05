package rsvpreader.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import kyo.*

/** Top-level layout composition for the RSVP reader app. */
object Layout:

  def header: HtmlElement = div(
    cls := "header",
    div(cls := "logo", "RSVP ", span("Reader")),
    div(
      cls := "status-indicator",
      div(cls <-- AppState.statusDotCls),
      child.text <-- AppState.statusText
    )
  )

  def readingTheater: HtmlElement = div(
    cls := "reading-theater",
    div(
      cls <-- AppState.focusContainerCls,
      div(cls := "orp-guides"),
      Components.focusWord
    ),
    Components.trailArea,
    Components.progressBar
  )

  def controlsDock(using AllowUnsafe): HtmlElement = div(
    cls := "controls-dock",
    Components.primaryControls,
    Components.secondaryControls
  )

  def paragraphModal: HtmlElement = div(
    cls <-- AppState.showParagraphView.signal.map { show =>
      if show then "paragraph-view visible" else "paragraph-view"
    },
    button(
      cls := "close-paragraph",
      "×",
      onClick --> (_ => AppState.showParagraphView.set(false))
    ),
    Components.paragraphContent
  )

  def app(onTextLoaded: String => Unit)(using AllowUnsafe): HtmlElement = div(
    header,
    readingTheater,
    controlsDock,
    paragraphModal,
    Components.textInputModal(onTextLoaded),
    Components.keyboardHints,
    Components.keyboardHandler,
    tabIndex := 0,
    onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Element].focus())
  )
