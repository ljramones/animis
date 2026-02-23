package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.runtime.blend.EvalContext;

public interface StateMachineEvaluator {
  void tick(StateMachineInstance sm, float dt, EvalContext ctx);
}
