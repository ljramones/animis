package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.runtime.ik.IkTarget;
import dev.ljramones.animis.runtime.pose.Pose;
import dev.ljramones.animis.runtime.skinning.SkinningOutput;

public interface AnimatorInstance {
  void setBool(String name, boolean value);

  void setFloat(String name, float value);

  void setIkTarget(String chainName, IkTarget target);

  void setEventListener(String eventName, Runnable listener);

  void clearEventListener(String eventName);

  void update(float deltaSeconds);

  Pose pose();

  SkinningOutput skinningOutput();

  RootMotionDelta rootMotionDelta();
}
