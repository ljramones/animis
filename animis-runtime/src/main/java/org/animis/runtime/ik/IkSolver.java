package org.animis.runtime.ik;

import org.animis.ik.IkChain;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Skeleton;

public interface IkSolver {
  void solve(PoseBuffer pose, Skeleton skeleton, IkChain chain, IkTarget target);
}
