package org.dynamisengine.animis.runtime.blend;

import org.dynamisengine.animis.blend.BlendNode;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;

public interface BlendEvaluator {
  void evaluate(BlendNode node, EvalContext ctx, PoseBuffer outPose);
}
