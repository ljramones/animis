package dev.ljramones.animis.runtime.blend;

import dev.ljramones.animis.blend.BlendNode;
import dev.ljramones.animis.runtime.pose.PoseBuffer;

public interface BlendEvaluator {
  void evaluate(BlendNode node, EvalContext ctx, PoseBuffer outPose);
}
