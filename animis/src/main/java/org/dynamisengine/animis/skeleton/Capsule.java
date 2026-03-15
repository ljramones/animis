package org.dynamisengine.animis.skeleton;

public record Capsule(
    float startX,
    float startY,
    float startZ,
    float endX,
    float endY,
    float endZ,
    float radius
) {
  public Capsule {
    if (radius < 0f) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
  }
}
