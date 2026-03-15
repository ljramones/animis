package org.dynamisengine.animis.runtime.api;

public record RootMotionDelta(float dx, float dy, float dz, float dyaw) {
  public static final RootMotionDelta ZERO = new RootMotionDelta(0f, 0f, 0f, 0f);

  public RootMotionDelta add(final RootMotionDelta other) {
    return new RootMotionDelta(
        this.dx + other.dx,
        this.dy + other.dy,
        this.dz + other.dz,
        this.dyaw + other.dyaw);
  }
}
