package org.dynamisengine.animis.runtime.api;

import java.util.Arrays;

public record MotionQuery(
    float[] currentPoseFeatures,
    float[] desiredTrajectory,
    float[] contactFlags
) {
  public MotionQuery {
    currentPoseFeatures = currentPoseFeatures == null ? new float[0] : Arrays.copyOf(currentPoseFeatures, currentPoseFeatures.length);
    desiredTrajectory = desiredTrajectory == null ? new float[0] : Arrays.copyOf(desiredTrajectory, desiredTrajectory.length);
    contactFlags = contactFlags == null ? new float[0] : Arrays.copyOf(contactFlags, contactFlags.length);
  }
}
