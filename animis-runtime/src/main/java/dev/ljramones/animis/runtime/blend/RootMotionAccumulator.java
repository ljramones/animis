package dev.ljramones.animis.runtime.blend;

import dev.ljramones.animis.runtime.api.RootMotionDelta;

public final class RootMotionAccumulator {
  private float dx;
  private float dy;
  private float dz;
  private float dyaw;

  public void add(final RootMotionDelta delta) {
    this.dx += delta.dx();
    this.dy += delta.dy();
    this.dz += delta.dz();
    this.dyaw += delta.dyaw();
  }

  public RootMotionDelta snapshot() {
    return new RootMotionDelta(this.dx, this.dy, this.dz, this.dyaw);
  }

  public void reset() {
    this.dx = 0f;
    this.dy = 0f;
    this.dz = 0f;
    this.dyaw = 0f;
  }
}
