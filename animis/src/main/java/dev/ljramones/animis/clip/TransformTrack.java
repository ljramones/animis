package dev.ljramones.animis.clip;

public record TransformTrack(
    int jointIndex,
    TrackMetadata metadata,
    float[] translations,
    float[] rotations,
    float[] scales
) {}
