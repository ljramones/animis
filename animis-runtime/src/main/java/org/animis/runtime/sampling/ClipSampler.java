package org.animis.runtime.sampling;

import org.animis.clip.Clip;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Skeleton;

public interface ClipSampler {
  ClipSampleResult sample(
      Clip clip,
      Skeleton skeleton,
      float timeSeconds,
      float previousTimeSeconds,
      boolean loop,
      PoseBuffer outPose);
}
