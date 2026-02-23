package org.animis.runtime.api;

import org.animis.motion.MotionDatabase;
import org.animis.motion.MotionFrame;

public final class DefaultMotionMatcher implements MotionMatcher {
  private final float[] poseWeights;
  private final float[] trajectoryWeights;
  private final float[] contactWeights;

  public DefaultMotionMatcher() {
    this(new float[0], new float[0], new float[0]);
  }

  public DefaultMotionMatcher(
      final float[] poseWeights,
      final float[] trajectoryWeights,
      final float[] contactWeights) {
    this.poseWeights = poseWeights == null ? new float[0] : java.util.Arrays.copyOf(poseWeights, poseWeights.length);
    this.trajectoryWeights = trajectoryWeights == null ? new float[0] : java.util.Arrays.copyOf(trajectoryWeights, trajectoryWeights.length);
    this.contactWeights = contactWeights == null ? new float[0] : java.util.Arrays.copyOf(contactWeights, contactWeights.length);
  }

  @Override
  public MotionFrame findBest(final MotionDatabase db, final MotionQuery query) {
    if (db.frames() == null || db.frames().isEmpty()) {
      throw new IllegalArgumentException("MotionDatabase has no frames");
    }

    MotionFrame best = null;
    float bestScore = Float.POSITIVE_INFINITY;
    for (final MotionFrame frame : db.frames()) {
      validateComparable(frame, query);
      final float score =
          weightedSquaredDistance(frame.poseFeatures(), query.currentPoseFeatures(), this.poseWeights)
              + weightedSquaredDistance(frame.trajectoryFeatures(), query.desiredTrajectory(), this.trajectoryWeights)
              + weightedSquaredDistance(frame.contactFlags(), query.contactFlags(), this.contactWeights);
      if (score < bestScore) {
        bestScore = score;
        best = frame;
      }
    }
    return best;
  }

  private static void validateComparable(final MotionFrame frame, final MotionQuery query) {
    if (frame.poseFeatures().length != query.currentPoseFeatures().length) {
      throw new IllegalArgumentException("Pose feature length mismatch between frame and query");
    }
    if (frame.trajectoryFeatures().length != query.desiredTrajectory().length) {
      throw new IllegalArgumentException("Trajectory feature length mismatch between frame and query");
    }
    if (frame.contactFlags().length != query.contactFlags().length) {
      throw new IllegalArgumentException("Contact feature length mismatch between frame and query");
    }
  }

  private static float weightedSquaredDistance(
      final float[] a,
      final float[] b,
      final float[] weights) {
    float acc = 0f;
    for (int i = 0; i < a.length; i++) {
      final float d = a[i] - b[i];
      final float w = i < weights.length ? Math.max(0f, weights[i]) : 1f;
      acc += w * d * d;
    }
    return acc;
  }
}
