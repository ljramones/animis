package org.animis.runtime.secondary;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Skeleton;

public interface SecondaryMotionSolver {
  void solve(PoseBuffer pose, Skeleton skeleton, float dt);
}
