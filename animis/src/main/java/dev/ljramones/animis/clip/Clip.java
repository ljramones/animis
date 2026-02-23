package dev.ljramones.animis.clip;

import java.util.List;
import java.util.Optional;

public record Clip(
    ClipId id,
    String name,
    float durationSeconds,
    List<TransformTrack> tracks,
    Optional<RootMotionDef> rootMotion,
    List<AnimationEvent> events
) {
  public Clip(final ClipId id, final String name, final float durationSeconds, final List<TransformTrack> tracks) {
    this(id, name, durationSeconds, tracks, Optional.empty(), List.of());
  }

  public Clip(
      final ClipId id,
      final String name,
      final float durationSeconds,
      final List<TransformTrack> tracks,
      final Optional<RootMotionDef> rootMotion) {
    this(id, name, durationSeconds, tracks, rootMotion, List.of());
  }
}
