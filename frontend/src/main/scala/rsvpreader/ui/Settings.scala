package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
import org.scalajs.dom
import kyo.*
import rsvpreader.*

/** Settings modal component for configuring keybindings and center mode. */
object Settings:

  def modal(using AllowUnsafe): HtmlElement = div(
    cls <-- AppState.showSettingsModal.signal.map { show =>
      if show then "settings-modal visible" else "settings-modal"
    },
    div(
      cls := "settings-content",
      div(
        cls := "settings-header",
        h2("Settings"),
        button(
          cls := "close-settings",
          "×",
          onClick --> (_ => AppState.showSettingsModal.set(false))
        )
      ),
      centerModeSection,
      contextSentencesSection,
      keybindingsSection,
      div(
        cls := "settings-footer",
        button(
          cls := "reset-btn",
          "Reset to Defaults",
          onClick --> { _ =>
            AppState.currentCenterMode.set(CenterMode.ORP)
            AppState.currentKeyBindings.set(KeyBindings.default)
            AppState.contextSentences.set(1)
            AppState.saveSettings()
          }
        )
      )
    )
  )

  private def centerModeSection: HtmlElement = div(
    cls := "settings-section",
    h3("Center Mode"),
    p(cls := "settings-desc", "How the focus word is positioned"),
    div(
      cls := "radio-group",
      radioOption("ORP letter (Recommended)", CenterMode.ORP),
      radioOption("First letter", CenterMode.First),
      radioOption("No centering", CenterMode.None)
    )
  )

  private def radioOption(labelText: String, mode: CenterMode): HtmlElement = label(
    cls := "radio-option",
    input(
      typ := "radio",
      nameAttr := "centerMode",
      checked <-- AppState.currentCenterMode.signal.map(_ == mode),
      onChange --> { _ =>
        AppState.currentCenterMode.set(mode)
        AppState.saveSettings()
      }
    ),
    span(labelText)
  )

  private def contextSentencesSection: HtmlElement = div(
    cls := "settings-section",
    h3("Context Sentences"),
    p(cls := "settings-desc", "Number of sentences shown during playback"),
    div(
      cls := "segmented-control",
      (1 to 4).map { n =>
        button(
          cls <-- AppState.contextSentences.signal.map { current =>
            if current == n then "segment-btn active" else "segment-btn"
          },
          n.toString,
          onClick --> { _ =>
            AppState.contextSentences.set(n)
            AppState.saveSettings()
          }
        )
      }
    )
  )

  private def keybindingsSection(using AllowUnsafe): HtmlElement = div(
    cls := "settings-section",
    h3("Keyboard Shortcuts"),
    div(
      cls := "keybindings-list",
      KeyAction.values.toSeq.map(keybindingRow)
    )
  )

  private def keybindingRow(action: KeyAction)(using AllowUnsafe): HtmlElement = div(
    cls := "keybinding-row",
    span(cls := "action-name", actionLabel(action)),
    button(
      cls <-- AppState.capturingKeyFor.signal.map {
        case Some(a) if a == action => "key-capture capturing"
        case _                      => "key-capture"
      },
      child.text <-- AppState.capturingKeyFor.signal.combineWith(AppState.currentKeyBindings.signal).map {
        case (Some(a), _) if a == action => "Press a key..."
        case (_, bindings) =>
          bindings.keyFor(action) match
            case " " => "Space"
            case k   => k
      },
      onClick --> (_ => AppState.capturingKeyFor.set(Some(action)))
    )
  )

  private def actionLabel(action: KeyAction): String = action match
    case KeyAction.PlayPause        => "Play / Pause"
    case KeyAction.RestartSentence  => "Restart sentence"
    case KeyAction.RestartParagraph => "Restart paragraph"
    case KeyAction.SpeedUp          => "Speed up"
    case KeyAction.SpeedDown        => "Speed down"
