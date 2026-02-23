package dev.ljramones.animis.runtime.skinning;

import dev.ljramones.animis.runtime.pose.Pose;
import dev.ljramones.animis.skeleton.Skeleton;

public interface SkinningComputer {
  SkinningOutput compute(Skeleton skeleton, Pose pose);
}
