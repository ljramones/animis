package dev.ljramones.animis.common;

public final class Validation {
  private Validation() {}

  public static void requireJointCount(int jointCount) {
    if (jointCount < 0) {
      throw new IllegalArgumentException("jointCount must be >= 0");
    }
  }
}
