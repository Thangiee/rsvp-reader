package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import rsvpreader.*

/** Transient UI state that does not belong in the domain model.
  *
  * These are UI-only concerns like modal visibility and text input that
  * are not persisted or reduced through the Action/Reducer pipeline.
  */
case class UiState(
  showTextInputModal: LaminarVar[Boolean],
  showSettingsModal: LaminarVar[Boolean],
  inputText: LaminarVar[String],
  loadError: LaminarVar[Option[String]],
  capturingKeyFor: LaminarVar[Option[KeyAction]]
)

object UiState:
  def initial(inputText: String = "", showTextInput: Boolean = true): UiState = UiState(
    showTextInputModal = LaminarVar(showTextInput),
    showSettingsModal = LaminarVar(false),
    inputText = LaminarVar(inputText),
    loadError = LaminarVar(None),
    capturingKeyFor = LaminarVar(None)
  )
