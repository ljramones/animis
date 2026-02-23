package org.animis.runtime.api;

import org.animis.skeleton.Skeleton;
import org.animis.state.StateMachineDef;

public interface AnimationRuntime {
  AnimatorInstance create(StateMachineDef machine, Skeleton skeleton);
}
