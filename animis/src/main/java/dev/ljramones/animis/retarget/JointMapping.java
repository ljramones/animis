package dev.ljramones.animis.retarget;

import java.util.Optional;

public record JointMapping(
    String sourceName,
    String targetName,
    Optional<float[]> rotationOffset
) {}
