package org.dynamisengine.animis.runtime.physics;

import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface PhysicsCharacterController {
  void update(PoseBuffer targetPose, Skeleton skeleton, float dt);

  PoseBuffer simulatedPose();
}
