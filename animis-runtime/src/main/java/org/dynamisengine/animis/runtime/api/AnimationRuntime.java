package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.skeleton.Skeleton;
import org.dynamisengine.animis.state.StateMachineDef;

public interface AnimationRuntime {
  AnimatorInstance create(StateMachineDef machine, Skeleton skeleton);
}
