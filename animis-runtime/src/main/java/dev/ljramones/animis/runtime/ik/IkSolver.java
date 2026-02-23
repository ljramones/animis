package dev.ljramones.animis.runtime.ik;

import dev.ljramones.animis.ik.IkChain;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.Skeleton;

public interface IkSolver {
  void solve(PoseBuffer pose, Skeleton skeleton, IkChain chain, IkTarget target);
}
