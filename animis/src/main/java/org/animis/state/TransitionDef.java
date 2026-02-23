package org.animis.state;

public record TransitionDef(
    String toState,
    ConditionExpr condition,
    float blendSeconds,
    boolean hasExitTime,
    float exitTimeNormalized,
    float halfLife
) {
  public TransitionDef(
      final String toState,
      final ConditionExpr condition,
      final float blendSeconds,
      final boolean hasExitTime,
      final float exitTimeNormalized) {
    this(toState, condition, blendSeconds, hasExitTime, exitTimeNormalized, 0.15f);
  }
}
