package org.dynamisengine.animis.neural.api;

import java.util.Arrays;

public record PosePrediction(float[] predictedPose, float[] predictedTrajectory, float confidence) {
  public PosePrediction {
    if (predictedPose == null || predictedTrajectory == null) {
      throw new IllegalArgumentException("predictedPose and predictedTrajectory must be non-null");
    }
    predictedPose = Arrays.copyOf(predictedPose, predictedPose.length);
    predictedTrajectory = Arrays.copyOf(predictedTrajectory, predictedTrajectory.length);
  }
}
