package dev.ljramones.animis.runtime.sampling;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.Skeleton;

public interface ClipSampler {
  ClipSampleResult sample(
      Clip clip,
      Skeleton skeleton,
      float timeSeconds,
      float previousTimeSeconds,
      boolean loop,
      PoseBuffer outPose);
}
