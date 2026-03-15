package org.dynamisengine.animis.blend;

public record WeightShiftNode(
    int hipJoint,
    float amplitudeMeters,
    float cycleSeconds,
    String idleTimeParameter
) implements ProceduralNode {}
