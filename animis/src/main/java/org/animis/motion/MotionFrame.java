package org.animis.motion;

import java.util.Arrays;

public record MotionFrame(
    int clipIndex,
    float timeSeconds,
    float[] poseFeatures,
    float[] trajectoryFeatures,
    float[] contactFlags
) {
  public MotionFrame {
    poseFeatures = poseFeatures == null ? new float[0] : Arrays.copyOf(poseFeatures, poseFeatures.length);
    trajectoryFeatures = trajectoryFeatures == null ? new float[0] : Arrays.copyOf(trajectoryFeatures, trajectoryFeatures.length);
    contactFlags = contactFlags == null ? new float[0] : Arrays.copyOf(contactFlags, contactFlags.length);
  }
}
