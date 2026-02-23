package org.animis.neural.api;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class OnnxNeuralPosePredictor implements NeuralPosePredictor {
  private final NeuralModelInfo modelInfo;
  private final OrtEnvironment environment;
  private final OrtSession session;

  public OnnxNeuralPosePredictor(Path modelPath, NeuralModelInfo modelInfo) {
    this(readModelBytes(modelPath), modelInfo);
  }

  public OnnxNeuralPosePredictor(InputStream modelInputStream, NeuralModelInfo modelInfo) {
    this(readModelBytes(modelInputStream), modelInfo);
  }

  public OnnxNeuralPosePredictor(byte[] modelBytes, NeuralModelInfo modelInfo) {
    if (modelBytes == null || modelBytes.length == 0) {
      throw new IllegalArgumentException("modelBytes must be non-empty");
    }
    this.modelInfo = modelInfo;
    try {
      this.environment = OrtEnvironment.getEnvironment();
      OrtSession.SessionOptions options = new OrtSession.SessionOptions();
      options.setIntraOpNumThreads(1);
      options.setInterOpNumThreads(1);
      options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
      this.session = environment.createSession(modelBytes, options);
    } catch (OrtException exception) {
      throw new IllegalArgumentException("Unable to initialize ONNX Runtime session", exception);
    }
  }

  @Override
  public PosePrediction predict(float[] poseFeatures, float[] velocityFeatures) {
    if (poseFeatures == null || velocityFeatures == null) {
      throw new IllegalArgumentException("poseFeatures and velocityFeatures must be non-null");
    }
    if (poseFeatures.length != modelInfo.poseFeatureDimensions()) {
      throw new IllegalArgumentException(
          "Expected poseFeatures length "
              + modelInfo.poseFeatureDimensions()
              + " but was "
              + poseFeatures.length);
    }
    if (velocityFeatures.length != modelInfo.velocityFeatureDimensions()) {
      throw new IllegalArgumentException(
          "Expected velocityFeatures length "
              + modelInfo.velocityFeatureDimensions()
              + " but was "
              + velocityFeatures.length);
    }

    float[] input = new float[poseFeatures.length + velocityFeatures.length];
    System.arraycopy(poseFeatures, 0, input, 0, poseFeatures.length);
    System.arraycopy(velocityFeatures, 0, input, poseFeatures.length, velocityFeatures.length);

    try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[] {1, input.length});
        OrtSession.Result output = session.run(Map.of(modelInfo.inputTensorName(), inputTensor))) {
      float[] predictedPose = extractVector(output, modelInfo.outputPoseTensorName(), modelInfo.predictedPoseDimensions());
      float[] predictedTrajectory =
          extractVector(output, modelInfo.outputTrajectoryTensorName(), modelInfo.predictedTrajectoryDimensions());
      float confidence = extractConfidence(output);
      return new PosePrediction(predictedPose, predictedTrajectory, confidence);
    } catch (OrtException exception) {
      throw new IllegalStateException("Failed to run neural prediction", exception);
    }
  }

  @Override
  public NeuralModelInfo modelInfo() {
    return modelInfo;
  }

  @Override
  public void close() {
    try {
      session.close();
    } catch (OrtException exception) {
      throw new IllegalStateException("Failed to close ONNX Runtime session", exception);
    }
  }

  private float[] extractVector(OrtSession.Result output, String tensorName, int expectedSize) throws OrtException {
    OnnxValue value = output.get(tensorName).orElse(null);
    if (value == null) {
      throw new IllegalStateException("Missing output tensor: " + tensorName);
    }
    if (!(value instanceof OnnxTensor tensor)) {
      throw new IllegalStateException("Output is not a tensor: " + tensorName);
    }

    Object rawValue = tensor.getValue();
    float[] vector;
    if (rawValue instanceof float[] oneDimensional) {
      vector = oneDimensional;
    } else if (rawValue instanceof float[][] twoDimensional) {
      if (twoDimensional.length == 0) {
        throw new IllegalStateException("Output tensor is empty: " + tensorName);
      }
      vector = twoDimensional[0];
    } else {
      throw new IllegalStateException("Unsupported output tensor type for " + tensorName + ": " + rawValue.getClass());
    }

    if (vector.length != expectedSize) {
      throw new IllegalStateException(
          "Output tensor "
              + tensorName
              + " expected length "
              + expectedSize
              + " but was "
              + vector.length);
    }
    return vector;
  }

  private float extractConfidence(OrtSession.Result output) throws OrtException {
    String confidenceTensorName = modelInfo.outputConfidenceTensorName();
    if (confidenceTensorName == null || confidenceTensorName.isBlank()) {
      return 1.0f;
    }

    OnnxValue value = output.get(confidenceTensorName).orElse(null);
    if (value == null) {
      throw new IllegalStateException("Missing confidence tensor: " + confidenceTensorName);
    }
    if (!(value instanceof OnnxTensor tensor)) {
      throw new IllegalStateException("Confidence output is not a tensor: " + confidenceTensorName);
    }

    Object raw = tensor.getValue();
    if (raw instanceof float[] confidenceVector && confidenceVector.length > 0) {
      return confidenceVector[0];
    }
    if (raw instanceof float[][] confidenceMatrix
        && confidenceMatrix.length > 0
        && confidenceMatrix[0].length > 0) {
      return confidenceMatrix[0][0];
    }
    if (raw instanceof Float confidenceScalar) {
      return confidenceScalar;
    }

    throw new IllegalStateException("Unsupported confidence tensor type: " + raw.getClass());
  }

  private static byte[] readModelBytes(Path modelPath) {
    try {
      return Files.readAllBytes(modelPath);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read model path: " + modelPath, exception);
    }
  }

  private static byte[] readModelBytes(InputStream inputStream) {
    if (inputStream == null) {
      throw new IllegalArgumentException("modelInputStream must be non-null");
    }
    try {
      return inputStream.readAllBytes();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read model InputStream", exception);
    }
  }
}
