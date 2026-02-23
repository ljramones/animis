package org.animis.neural.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OnnxNeuralPosePredictorTest {
  @Test
  void loadsModelFromTestResource() throws Exception {
    try (InputStream modelStream = resourceModelStream();
        OnnxNeuralPosePredictor predictor = new OnnxNeuralPosePredictor(modelStream, modelInfo())) {
      assertNotNull(predictor.modelInfo());
      assertEquals("input", predictor.modelInfo().inputTensorName());
    }
  }

  @Test
  void predictReturnsConfiguredOutputShapes() throws Exception {
    try (InputStream modelStream = resourceModelStream();
        OnnxNeuralPosePredictor predictor = new OnnxNeuralPosePredictor(modelStream, modelInfo())) {
      PosePrediction prediction = predictor.predict(new float[] {1.0f, 2.0f}, new float[] {3.0f, 4.0f});
      assertEquals(4, prediction.predictedPose().length);
      assertEquals(4, prediction.predictedTrajectory().length);
      assertEquals(1.0f, prediction.confidence());
    }
  }

  @Test
  void mismatchedInputDimensionsThrowClearMessage() throws Exception {
    try (InputStream modelStream = resourceModelStream();
        OnnxNeuralPosePredictor predictor = new OnnxNeuralPosePredictor(modelStream, modelInfo())) {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> predictor.predict(new float[] {1.0f}, new float[] {2.0f, 3.0f}));
      assertEquals("Expected poseFeatures length 2 but was 1", exception.getMessage());
    }
  }

  private static NeuralModelInfo modelInfo() {
    return new NeuralModelInfo(
        "input",
        "predicted_pose",
        "predicted_trajectory",
        null,
        2,
        2,
        4,
        4,
        Map.of("model", "identity-test"));
  }

  private static InputStream resourceModelStream() {
    InputStream modelStream =
        OnnxNeuralPosePredictorTest.class
            .getClassLoader()
            .getResourceAsStream("models/identity_dual_output.onnx");
    if (modelStream == null) {
      throw new IllegalStateException("Missing test model resource: models/identity_dual_output.onnx");
    }
    return modelStream;
  }
}
