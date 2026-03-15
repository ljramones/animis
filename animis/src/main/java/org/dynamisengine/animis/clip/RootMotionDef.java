package org.dynamisengine.animis.clip;

public record RootMotionDef(
    int rootJoint,
    boolean extractTranslationXZ,
    boolean extractTranslationY,
    boolean extractRotationY
) {}
