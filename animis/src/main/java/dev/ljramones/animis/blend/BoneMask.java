package dev.ljramones.animis.blend;

import java.util.Arrays;

public record BoneMask(String name, float[] jointWeights) {
  public BoneMask {
    jointWeights = jointWeights == null ? new float[0] : Arrays.copyOf(jointWeights, jointWeights.length);
  }

  public float weight(final int jointIndex) {
    if (jointIndex < 0 || jointIndex >= this.jointWeights.length) {
      return 0f;
    }
    return clamp01(this.jointWeights[jointIndex]);
  }

  private static float clamp01(final float value) {
    return Math.max(0f, Math.min(1f, value));
  }
}
