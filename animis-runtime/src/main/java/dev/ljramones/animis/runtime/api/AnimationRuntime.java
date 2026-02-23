package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.skeleton.Skeleton;
import dev.ljramones.animis.state.StateMachineDef;

public interface AnimationRuntime {
  AnimatorInstance create(StateMachineDef machine, Skeleton skeleton);
}
