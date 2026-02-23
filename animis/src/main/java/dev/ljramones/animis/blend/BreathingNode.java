package dev.ljramones.animis.blend;

public record BreathingNode(
    int spineJoint,
    float amplitudeRadians,
    float cycleSeconds,
    String exhaustionParameter
) implements ProceduralNode {}
