package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import kyo.*
import rsvpreader.config.*

/** Transient UI state that does not belong in the domain model.
  *
  * These are UI-only concerns like modal visibility and text input that
  * are not persisted or reduced through the Action/Reducer pipeline.
  */
case class UiState(
  showTextInputModal: LaminarVar[Boolean],
  showSettingsModal: LaminarVar[Boolean],
  inputText: LaminarVar[String],
  loadError: LaminarVar[Maybe[String]],
  capturingKeyFor: LaminarVar[Maybe[KeyAction]]
)

object UiState:
  def initial(inputText: String = "", showTextInput: Boolean = true): UiState = UiState(
    showTextInputModal = LaminarVar(showTextInput),
    showSettingsModal = LaminarVar(false),
    inputText = LaminarVar(inputText),
    loadError = LaminarVar(Absent),
    capturingKeyFor = LaminarVar(Absent)
  )
