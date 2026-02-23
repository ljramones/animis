package dev.ljramones.animis.clip;

import java.util.List;

public record Clip(
    ClipId id,
    String name,
    float durationSeconds,
    List<TransformTrack> tracks
) {}
