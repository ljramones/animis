package org.animis.runtime.physics;

import java.util.List;

public record PhysicsCharacterDef(
    float stiffness,
    float damping,
    float maxTorque,
    List<Integer> drivenJoints,
    List<Integer> keyframedJoints
) {
  public PhysicsCharacterDef {
    if (stiffness < 0f || stiffness > 1f) {
      throw new IllegalArgumentException("stiffness must be in [0, 1]");
    }
    if (damping < 0f || damping > 1f) {
      throw new IllegalArgumentException("damping must be in [0, 1]");
    }
    if (maxTorque < 0f) {
      throw new IllegalArgumentException("maxTorque must be >= 0");
    }
    drivenJoints = drivenJoints == null ? List.of() : List.copyOf(drivenJoints);
    keyframedJoints = keyframedJoints == null ? List.of() : List.copyOf(keyframedJoints);
  }
}
