package org.animis.runtime.state;

import org.animis.runtime.blend.EvalContext;
import org.animis.runtime.pose.PoseBuffer;

public interface StateMachineEvaluator {
  void tick(StateMachineInstance sm, float dt, EvalContext ctx, PoseBuffer outPose);
}
