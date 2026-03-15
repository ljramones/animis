package org.dynamisengine.animis.motion;

import java.util.List;

public record PoseSearchIndex(List<TaggedFrame> frames) {
  public PoseSearchIndex {
    frames = frames == null ? List.of() : List.copyOf(frames);
  }
}
