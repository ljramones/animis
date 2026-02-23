package org.animis.warp;

import java.util.Arrays;

public record WarpTarget(
    String name,
    int joint,
    float[] worldPosition,
    float maxTranslationMeters,
    float maxRotationRadians
) {
  public WarpTarget {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must be non-blank");
    }
    if (joint < 0) {
      throw new IllegalArgumentException("joint must be >= 0");
    }
    if (worldPosition == null || worldPosition.length != 3) {
      throw new IllegalArgumentException("worldPosition must have length 3");
    }
    worldPosition = Arrays.copyOf(worldPosition, 3);
    if (maxTranslationMeters < 0f) {
      throw new IllegalArgumentException("maxTranslationMeters must be >= 0");
    }
    if (maxRotationRadians < 0f) {
      throw new IllegalArgumentException("maxRotationRadians must be >= 0");
    }
  }
}
