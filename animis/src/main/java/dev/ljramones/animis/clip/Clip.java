package dev.ljramones.animis.clip;

import java.util.List;
import java.util.Optional;

public record Clip(
    ClipId id,
    String name,
    float durationSeconds,
    List<TransformTrack> tracks,
    Optional<RootMotionDef> rootMotion
) {
  public Clip(final ClipId id, final String name, final float durationSeconds, final List<TransformTrack> tracks) {
    this(id, name, durationSeconds, tracks, Optional.empty());
  }
}
