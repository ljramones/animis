package org.dynamisengine.animis.runtime.warp;

import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Skeleton;
import org.dynamisengine.animis.warp.WarpTarget;
import java.util.List;

public interface PoseWarper {
  void warp(PoseBuffer pose, Skeleton skeleton, List<WarpTarget> targets);
}
