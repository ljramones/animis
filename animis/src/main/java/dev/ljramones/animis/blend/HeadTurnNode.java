package dev.ljramones.animis.blend;

public record HeadTurnNode(
    int headJoint,
    String targetYawParameter,
    String targetPitchParameter,
    float maxYawRadians,
    float maxPitchRadians,
    float trackingSpeed
) implements ProceduralNode {}
