package org.animis.loader;

import org.animis.clip.Clip;
import org.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Map;

public record AnimationLoadResult(
    List<Skeleton> skeletons,
    List<Clip> clips,
    Map<Integer, Integer> clipToSkeleton
) {
  public AnimationLoadResult {
    skeletons = skeletons == null ? List.of() : List.copyOf(skeletons);
    clips = clips == null ? List.of() : List.copyOf(clips);
    clipToSkeleton = clipToSkeleton == null ? Map.of() : Map.copyOf(clipToSkeleton);
  }
}
