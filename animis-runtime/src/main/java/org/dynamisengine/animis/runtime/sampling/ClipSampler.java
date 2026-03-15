package org.dynamisengine.animis.runtime.sampling;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface ClipSampler {
  ClipSampleResult sample(
      Clip clip,
      Skeleton skeleton,
      float timeSeconds,
      float previousTimeSeconds,
      boolean loop,
      PoseBuffer outPose);
}
