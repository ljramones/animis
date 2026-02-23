package org.animis.runtime.api;

import org.animis.runtime.ik.IkTarget;
import org.animis.runtime.pose.Pose;
import org.animis.runtime.skinning.SkinningOutput;
import org.animis.warp.WarpTarget;

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
