package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.runtime.ik.IkTarget;
import org.dynamisengine.animis.runtime.pose.Pose;
import org.dynamisengine.animis.runtime.skinning.SkinningOutput;
import org.dynamisengine.animis.warp.WarpTarget;

public interface AnimatorInstance {
  void setBool(String name, boolean value);

  void setFloat(String name, float value);

  void setIkTarget(String chainName, IkTarget target);

  void setWarpTarget(WarpTarget target);

  void clearWarpTarget(String name);

  void setEventListener(String eventName, Runnable listener);

  void clearEventListener(String eventName);

  void update(float deltaSeconds);

  Pose pose();

  SkinningOutput skinningOutput();

  RootMotionDelta rootMotionDelta();
}
