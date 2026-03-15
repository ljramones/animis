package org.dynamisengine.animis.motion;

import java.util.List;

public record TaggedFrame(int clipIndex, float timeSeconds, List<PoseTag> tags) {
  public TaggedFrame {
    if (clipIndex < 0) {
      throw new IllegalArgumentException("clipIndex must be >= 0");
    }
    if (timeSeconds < 0f) {
      throw new IllegalArgumentException("timeSeconds must be >= 0");
    }
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
