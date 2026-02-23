package dev.ljramones.animis.runtime.pose;

import dev.ljramones.animis.common.Validation;
import java.util.Arrays;

public record Pose(
    float[] localTranslations,
    float[] localRotations,
    float[] localScales,
    int jointCount
) {
  public Pose {
    Validation.requireJointCount(jointCount);
    localTranslations = Arrays.copyOf(localTranslations, localTranslations.length);
    localRotations = Arrays.copyOf(localRotations, localRotations.length);
    localScales = Arrays.copyOf(localScales, localScales.length);
  }
}
