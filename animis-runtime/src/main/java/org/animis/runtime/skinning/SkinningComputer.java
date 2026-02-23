package org.animis.runtime.skinning;

import org.animis.runtime.pose.Pose;
import org.animis.skeleton.Skeleton;

public interface SkinningComputer {
  SkinningOutput compute(Skeleton skeleton, Pose pose);
}
