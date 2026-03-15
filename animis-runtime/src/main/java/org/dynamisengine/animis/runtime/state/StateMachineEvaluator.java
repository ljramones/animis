package org.dynamisengine.animis.runtime.state;

import org.dynamisengine.animis.runtime.blend.EvalContext;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;

public interface StateMachineEvaluator {
  void tick(StateMachineInstance sm, float dt, EvalContext ctx, PoseBuffer outPose);
}
