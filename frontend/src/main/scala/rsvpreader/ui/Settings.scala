package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
import kyo.*
import org.scalajs.dom
import rsvpreader.config.*
import rsvpreader.state.*

/** Settings modal component for configuring keybindings and center mode. */
object Settings:

  def modal(domain: AppContext, ui: UiState): HtmlElement = div(
    cls <-- ui.showSettingsModal.signal.map { show =>
      if show then "settings-modal visible" else "settings-modal"
    },
    div(
      cls := "settings-content",
      div(
        cls := "settings-header",
        h2("Settings"),
        button(
          cls := "close-settings",
          "\u00d7",
          onClick --> (_ => ui.showSettingsModal.set(false))
        )
      ),
      centerModeSection(domain),
      contextSentencesSection(domain),
      keybindingsSection(domain, ui),
      div(
        cls := "settings-footer",
        button(
          cls := "reset-btn",
          "Reset to Defaults",
          onClick --> { _ =>
            domain.dispatch(Action.SetCenterMode(CenterMode.ORP))
            domain.dispatch(Action.SetContextSentences(1))
            KeyBindings.default.bindings.foreach { (action, key) =>
              domain.dispatch(Action.UpdateKeyBinding(action, key))
            }
          }
        )
      )
    )
  )

  private def centerModeSection(domain: AppContext): HtmlElement = div(
    cls := "settings-section",
    h3("Center Mode"),
    p(cls := "settings-desc", "How the focus word is positioned"),
    div(
      cls := "radio-group",
      radioOption(domain, "ORP letter (Recommended)", CenterMode.ORP),
      radioOption(domain, "First letter", CenterMode.First),
      radioOption(domain, "No centering", CenterMode.None)
    )
  )

  private def radioOption(domain: AppContext, labelText: String, mode: CenterMode): HtmlElement = label(
    cls := "radio-option",
    input(
      typ := "radio",
      nameAttr := "centerMode",
      checked <-- domain.state.map(_.centerMode == mode),
      onChange --> { _ =>
        domain.dispatch(Action.SetCenterMode(mode))
      }
    ),
    span(labelText)
  )

  private def contextSentencesSection(domain: AppContext): HtmlElement = div(
    cls := "settings-section",
    h3("Context Sentences"),
    p(cls := "settings-desc", "Number of sentences shown during playback"),
    div(
      cls := "segmented-control",
      (1 to 4).map { n =>
        button(
          cls <-- domain.state.map { m =>
            if m.contextSentences == n then "segment-btn active" else "segment-btn"
          },
          n.toString,
          onClick --> { _ =>
            domain.dispatch(Action.SetContextSentences(n))
          }
        )
      }
    )
  )

  private def keybindingsSection(domain: AppContext, ui: UiState): HtmlElement = div(
    cls := "settings-section",
    h3("Keyboard Shortcuts"),
    div(
      cls := "keybindings-list",
      KeyAction.values.toSeq.map(keybindingRow(domain, ui, _))
    )
  )

  private def keybindingRow(domain: AppContext, ui: UiState, action: KeyAction): HtmlElement = div(
    cls := "keybinding-row",
    span(cls := "action-name", actionLabel(action)),
    button(
      cls <-- ui.capturingKeyFor.signal.map {
        case Present(a) if a == action => "key-capture capturing"
        case _                         => "key-capture"
      },
      child.text <-- ui.capturingKeyFor.signal.combineWith(domain.state.map(_.keyBindings)).map {
        case (Present(a), _) if a == action => "Press a key..."
        case (_, bindings) =>
          bindings.keyFor(action) match
            case " " => "Space"
            case k   => k
      },
      onClick --> (_ => ui.capturingKeyFor.set(Present(action)))
    )
  )

  private def actionLabel(action: KeyAction): String = action match
    case KeyAction.PlayPause        => "Play / Pause"
    case KeyAction.RestartSentence  => "Restart sentence"
    case KeyAction.RestartParagraph => "Restart paragraph"
    case KeyAction.SpeedUp          => "Speed up"
    case KeyAction.SpeedDown        => "Speed down"
