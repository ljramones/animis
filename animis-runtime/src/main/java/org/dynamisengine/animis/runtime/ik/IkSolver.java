package org.dynamisengine.animis.runtime.ik;

import org.dynamisengine.animis.ik.IkChain;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface IkSolver {
  void solve(PoseBuffer pose, Skeleton skeleton, IkChain chain, IkTarget target);
}
