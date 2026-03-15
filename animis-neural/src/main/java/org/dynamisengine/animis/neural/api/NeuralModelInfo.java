package org.dynamisengine.animis.neural.api;

import java.util.Map;

public record NeuralModelInfo(
    String inputTensorName,
    String outputPoseTensorName,
    String outputTrajectoryTensorName,
    String outputConfidenceTensorName,
    int poseFeatureDimensions,
    int velocityFeatureDimensions,
    int predictedPoseDimensions,
    int predictedTrajectoryDimensions,
    Map<String, String> metadata) {
  public NeuralModelInfo {
    inputTensorName = requireNonBlank(inputTensorName, "inputTensorName");
    outputPoseTensorName = requireNonBlank(outputPoseTensorName, "outputPoseTensorName");
    outputTrajectoryTensorName = requireNonBlank(outputTrajectoryTensorName, "outputTrajectoryTensorName");
    if (poseFeatureDimensions <= 0) {
      throw new IllegalArgumentException("poseFeatureDimensions must be > 0");
    }
    if (velocityFeatureDimensions <= 0) {
      throw new IllegalArgumentException("velocityFeatureDimensions must be > 0");
    }
    if (predictedPoseDimensions <= 0) {
      throw new IllegalArgumentException("predictedPoseDimensions must be > 0");
    }
    if (predictedTrajectoryDimensions <= 0) {
      throw new IllegalArgumentException("predictedTrajectoryDimensions must be > 0");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  private static String requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must be non-blank");
    }
    return value;
  }
}
