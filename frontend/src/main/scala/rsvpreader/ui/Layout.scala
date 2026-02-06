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
    Components.sentenceContext,
    Components.progressBar
  )

  def controlsDock(using AllowUnsafe): HtmlElement = div(
    cls := "controls-dock",
    Components.primaryControls,
    Components.secondaryControls
  )

  def app(onTextLoaded: String => Unit)(using AllowUnsafe): HtmlElement = div(
    header,
    readingTheater,
    controlsDock,
    Components.textInputModal(onTextLoaded),
    Settings.modal,
    Components.keyboardHints,
    Components.keyboardHandler,
    tabIndex := 0,
    onMountCallback { ctx =>
      ctx.thisNode.ref.asInstanceOf[dom.html.Element].focus()
    }
  )
