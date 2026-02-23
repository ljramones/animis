package org.animis.runtime.blend;

import org.animis.blend.BlendNode;
import org.animis.runtime.pose.PoseBuffer;

public interface BlendEvaluator {
  void evaluate(BlendNode node, EvalContext ctx, PoseBuffer outPose);
}
