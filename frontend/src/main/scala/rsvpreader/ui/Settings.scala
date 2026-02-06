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
    ),
    // Global key capture when capturing
    onKeyDown --> { event =>
      AppState.capturingKeyFor.now().foreach { action =>
        event.preventDefault()
        val key = event.key
        val updated = AppState.currentKeyBindings.now().withBinding(action, key)
        AppState.currentKeyBindings.set(updated)
        AppState.capturingKeyFor.set(None)
        AppState.saveSettings()
      }
    }
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
    child <-- AppState.capturingKeyFor.signal.combineWith(AppState.currentKeyBindings.signal).map {
      case (Some(a), _) if a == action =>
        span(cls := "key-capture capturing", "Press a key...")
      case (_, bindings) =>
        button(
          cls := "key-capture",
          bindings.keyFor(action) match
            case " " => "Space"
            case k   => k
          ,
          onClick --> (_ => AppState.capturingKeyFor.set(Some(action)))
        )
    }
  )

  private def actionLabel(action: KeyAction): String = action match
    case KeyAction.PlayPause       => "Play / Pause"
    case KeyAction.Back            => "Back 10 words"
    case KeyAction.RestartSentence => "Restart sentence"
    case KeyAction.ShowParagraph   => "Show paragraph"
    case KeyAction.CloseParagraph  => "Close paragraph"
    case KeyAction.SpeedUp         => "Speed up"
    case KeyAction.SpeedDown       => "Speed down"
    case KeyAction.Reset           => "Reset"
