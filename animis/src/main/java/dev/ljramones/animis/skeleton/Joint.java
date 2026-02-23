package dev.ljramones.animis.skeleton;

public record Joint(
    int index,
    String name,
    int parentIndex,
    BindTransform bind
) {}
