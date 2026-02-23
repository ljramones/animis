package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.runtime.blend.EvalContext;
import dev.ljramones.animis.runtime.pose.PoseBuffer;

public interface StateMachineEvaluator {
  void tick(StateMachineInstance sm, float dt, EvalContext ctx, PoseBuffer outPose);
}
