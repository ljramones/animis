package dev.ljramones.animis.state;

public record TransitionDef(
    String toState,
    ConditionExpr condition,
    float blendSeconds,
    boolean hasExitTime,
    float exitTimeNormalized
) {}
