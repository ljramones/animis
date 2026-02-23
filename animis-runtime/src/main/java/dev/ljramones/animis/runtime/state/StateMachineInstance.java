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
  private float[] lastTranslations;
  private float[] lastRotations;
  private float[] lastScales;
  private float lastDtSeconds;

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
    startTransition(toStateName, blendSeconds, 0.15f, null);
  }

  void startTransition(
      final String toStateName,
      final float blendSeconds,
      final float halfLife,
      final InertialState inertialState) {
    this.activeTransition =
        new ActiveTransition(
            this.currentStateName,
            toStateName,
            0f,
            Math.max(0f, blendSeconds),
            Math.max(1e-4f, halfLife),
            inertialState);
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

  void captureLastPose(final float[] translations, final float[] rotations, final float[] scales, final float dt) {
    this.lastTranslations = java.util.Arrays.copyOf(translations, translations.length);
    this.lastRotations = java.util.Arrays.copyOf(rotations, rotations.length);
    this.lastScales = java.util.Arrays.copyOf(scales, scales.length);
    this.lastDtSeconds = Math.max(0f, dt);
  }

  float[] lastTranslations() {
    return this.lastTranslations;
  }

  float[] lastRotations() {
    return this.lastRotations;
  }

  float[] lastScales() {
    return this.lastScales;
  }

  float lastDtSeconds() {
    return this.lastDtSeconds;
  }

  boolean hasLastPose() {
    return this.lastTranslations != null && this.lastRotations != null && this.lastScales != null;
  }

  public record ActiveTransition(
      String fromStateName,
      String toStateName,
      float elapsedSeconds,
      float blendSeconds,
      float halfLife,
      InertialState inertialState) {
    ActiveTransition withElapsed(final float elapsed) {
      return new ActiveTransition(
          this.fromStateName,
          this.toStateName,
          elapsed,
          this.blendSeconds,
          this.halfLife,
          this.inertialState);
    }
  }
}
