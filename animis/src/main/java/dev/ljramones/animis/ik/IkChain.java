package dev.ljramones.animis.ik;

import java.util.Optional;

public record IkChain(
    String name,
    int rootJoint,
    int midJoint,
    int tipJoint,
    Optional<Integer> poleTargetJoint,
    float minStretch,
    float maxStretch
) {}
