package org.animis.skeleton;

import java.util.List;

public record SecondaryChainDef(
    String name,
    List<Integer> joints,
    float stiffness,
    float damping,
    float angularLimit,
    List<Capsule> colliders
) {
  public SecondaryChainDef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must be non-blank");
    }
    joints = joints == null ? List.of() : List.copyOf(joints);
    colliders = colliders == null ? List.of() : List.copyOf(colliders);
    if (joints.isEmpty()) {
      throw new IllegalArgumentException("joints must not be empty");
    }
    if (stiffness < 0f || stiffness > 1f) {
      throw new IllegalArgumentException("stiffness must be in [0, 1]");
    }
    if (damping < 0f || damping > 1f) {
      throw new IllegalArgumentException("damping must be in [0, 1]");
    }
    if (angularLimit < 0f) {
      throw new IllegalArgumentException("angularLimit must be >= 0");
    }
  }
}
