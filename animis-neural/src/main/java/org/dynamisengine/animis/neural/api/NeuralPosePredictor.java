package org.dynamisengine.animis.neural.api;

public interface NeuralPosePredictor extends AutoCloseable {
  PosePrediction predict(float[] poseFeatures, float[] velocityFeatures);

  NeuralModelInfo modelInfo();

  @Override
  void close();
}
