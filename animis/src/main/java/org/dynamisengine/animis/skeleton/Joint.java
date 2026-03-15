package org.dynamisengine.animis.skeleton;

public record Joint(
    int index,
    String name,
    int parentIndex,
    BindTransform bind
) {}
