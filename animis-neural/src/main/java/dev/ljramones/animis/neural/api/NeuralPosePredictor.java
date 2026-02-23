package dev.ljramones.animis.neural.api;

import dev.ljramones.animis.runtime.pose.Pose;

public interface NeuralPosePredictor {
  Pose predict(Pose currentPose, float desiredVelocityX, float desiredVelocityY, float desiredVelocityZ, float dtSeconds);
}
