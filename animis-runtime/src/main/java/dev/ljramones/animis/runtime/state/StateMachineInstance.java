package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.state.StateDef;
import dev.ljramones.animis.state.StateMachineDef;
import java.util.HashMap;
import java.util.Map;

public final class StateMachineInstance {
  private final StateMachineDef definition;
  private final Map<String, StateDef> stateByName;
  private String currentStateName;
  private ActiveTransition activeTransition;

  public StateMachineInstance(final StateMachineDef definition) {
    this.definition = definition;
    this.stateByName = new HashMap<>();
    for (final StateDef state : definition.states()) {
      this.stateByName.put(state.name(), state);
    }
    this.currentStateName = definition.entryState();
  }

  public StateMachineDef definition() {
    return this.definition;
  }

  public String currentStateName() {
    return this.currentStateName;
  }

  public StateDef currentState() {
    return state(this.currentStateName);
  }

  public ActiveTransition activeTransition() {
    return this.activeTransition;
  }

  public boolean hasActiveTransition() {
    return this.activeTransition != null;
  }

  public StateDef state(final String stateName) {
    final StateDef state = this.stateByName.get(stateName);
    if (state == null) {
      throw new IllegalArgumentException("Unknown state: " + stateName);
    }
    return state;
  }

  void startTransition(final String toStateName, final float blendSeconds) {
    this.activeTransition = new ActiveTransition(this.currentStateName, toStateName, 0f, Math.max(0f, blendSeconds));
  }

  void advanceTransition(final float dtSeconds) {
    if (this.activeTransition == null) {
      return;
    }
    this.activeTransition = this.activeTransition.withElapsed(this.activeTransition.elapsedSeconds() + Math.max(0f, dtSeconds));
  }

  void completeTransition() {
    if (this.activeTransition == null) {
      return;
    }
    this.currentStateName = this.activeTransition.toStateName();
    this.activeTransition = null;
  }

  public record ActiveTransition(
      String fromStateName,
      String toStateName,
      float elapsedSeconds,
      float blendSeconds) {
    ActiveTransition withElapsed(final float elapsed) {
      return new ActiveTransition(this.fromStateName, this.toStateName, elapsed, this.blendSeconds);
    }
  }
}
