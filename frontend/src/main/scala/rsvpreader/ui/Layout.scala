package rsvpreader.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import rsvpreader.state.*

/** Top-level layout composition for the RSVP reader app. */
object Layout:

  def header(domain: DomainContext): HtmlElement = div(
    cls := "header",
    div(cls := "logo", "RSVP ", span("Reader")),
    div(
      cls := "status-indicator",
      div(cls <-- domain.model.map(DomainModel.statusDotCls)),
      child.text <-- domain.model.map(DomainModel.statusText)
    )
  )

  def readingTheater(domain: DomainContext): HtmlElement = div(
    cls := "reading-theater",
    div(cls := "theater-spacer"),
    div(
      cls <-- domain.model.map(DomainModel.focusContainerCls),
      div(cls := "orp-guides"),
      Components.focusWord(domain)
    ),
    Components.sentenceContext(domain),
    Components.progressBar(domain)
  )

  def controlsDock(domain: DomainContext, ui: UiState): HtmlElement = div(
    cls := "controls-dock",
    Components.primaryControls(domain),
    Components.secondaryControls(domain, ui)
  )

  def app(domain: DomainContext, ui: UiState, onTextLoaded: String => Unit): HtmlElement = div(
    cls := "app-root",
    header(domain),
    readingTheater(domain),
    controlsDock(domain, ui),
    Components.textInputModal(domain, ui, onTextLoaded),
    Settings.modal(domain, ui),
    Components.keyboardHints(domain),
    Components.keyboardHandler(domain, ui),
    tabIndex := 0,
    onMountCallback { ctx =>
      ctx.thisNode.ref.asInstanceOf[dom.html.Element].focus()
    }
  )
