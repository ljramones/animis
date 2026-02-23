package org.animis.runtime.physics;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Skeleton;

public interface PhysicsCharacterController {
  void update(PoseBuffer targetPose, Skeleton skeleton, float dt);

  PoseBuffer simulatedPose();
}
