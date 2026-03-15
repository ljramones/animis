package org.dynamisengine.animis.clip;

import java.util.Optional;

public record TransformTrack(
    int jointIndex,
    TrackMetadata metadata,
    float[] translations,
    float[] rotations,
    float[] scales,
    Optional<CompressedTrackData> compressed
) {
  public TransformTrack(
      final int jointIndex,
      final TrackMetadata metadata,
      final float[] translations,
      final float[] rotations,
      final float[] scales) {
    this(jointIndex, metadata, translations, rotations, scales, Optional.empty());
  }
}
