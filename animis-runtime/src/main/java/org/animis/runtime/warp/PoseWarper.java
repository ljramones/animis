package org.animis.runtime.warp;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Skeleton;
import org.animis.warp.WarpTarget;
import java.util.List;

public interface PoseWarper {
  void warp(PoseBuffer pose, Skeleton skeleton, List<WarpTarget> targets);
}
