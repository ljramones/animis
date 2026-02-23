package dev.ljramones.animis.skeleton;

import java.util.List;

public record Skeleton(
    String name,
    List<Joint> joints,
    int rootJoint
) {}
