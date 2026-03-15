package org.dynamisengine.animis.skeleton;

import java.util.List;

public record Skeleton(
    String name,
    List<Joint> joints,
    int rootJoint,
    List<SecondaryChainDef> secondaryChains
) {
  public Skeleton(String name, List<Joint> joints, int rootJoint) {
    this(name, joints, rootJoint, List.of());
  }

  public Skeleton {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must be non-blank");
    }
    joints = joints == null ? List.of() : List.copyOf(joints);
    secondaryChains = secondaryChains == null ? List.of() : List.copyOf(secondaryChains);
    if (rootJoint < 0 || rootJoint >= joints.size()) {
      throw new IllegalArgumentException("rootJoint out of bounds");
    }
    validateSecondaryChains(joints, secondaryChains);
  }

  private static void validateSecondaryChains(
      final List<Joint> joints,
      final List<SecondaryChainDef> chains) {
    for (final SecondaryChainDef chain : chains) {
      for (final int jointIndex : chain.joints()) {
        if (jointIndex < 0 || jointIndex >= joints.size()) {
          throw new IllegalArgumentException(
              "Secondary chain '" + chain.name() + "' has out-of-bounds joint index: " + jointIndex);
        }
      }
    }
  }
}
