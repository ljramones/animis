package org.dynamisengine.animis.runtime.skinning;

import org.dynamisengine.animis.runtime.pose.Pose;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface SkinningComputer {
  SkinningOutput compute(Skeleton skeleton, Pose pose);
}
