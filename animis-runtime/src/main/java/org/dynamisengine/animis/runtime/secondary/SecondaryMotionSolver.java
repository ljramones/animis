package org.dynamisengine.animis.runtime.secondary;

import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface SecondaryMotionSolver {
  void solve(PoseBuffer pose, Skeleton skeleton, float dt);
}
