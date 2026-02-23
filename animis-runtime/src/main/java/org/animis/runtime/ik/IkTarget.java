package org.animis.runtime.ik;

public record IkTarget(float x, float y, float z, float poleX, float poleY, float poleZ, boolean hasPole) {
  public static IkTarget withoutPole(float x, float y, float z) {
    return new IkTarget(x, y, z, 0f, 0f, 0f, false);
  }

  public static IkTarget withPole(float x, float y, float z, float poleX, float poleY, float poleZ) {
    return new IkTarget(x, y, z, poleX, poleY, poleZ, true);
  }
}
