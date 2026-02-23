package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.runtime.pose.Pose;

public interface AnimatorInstance {
  void setBool(String name, boolean value);

  void setFloat(String name, float value);

  void update(float deltaSeconds);

  Pose pose();
}
