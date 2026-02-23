package org.animis.runtime.state;

import org.animis.runtime.pose.PoseBuffer;

final class InertialBlendEvaluator {
  void apply(final InertialState state, final float halfLife, final float dt, final PoseBuffer targetPose, final PoseBuffer outPose) {
    final float[] targetT = targetPose.localTranslations();
    final float[] targetR = targetPose.localRotations();
    final float[] targetS = targetPose.localScales();

    final float[] tOffset = state.translationOffsets();
    final float[] tVel = state.translationVelocities();
    final float[] sOffset = state.scaleOffsets();
    final float[] sVel = state.scaleVelocities();
    final float[] rOffset = state.rotationOffsets();
    final float[] rVel = state.rotationVelocities();

    final float omega = (float) (2.0 * Math.PI / Math.max(1e-4f, halfLife));
    final float exp = (float) Math.exp(-omega * Math.max(0f, dt));

    for (int i = 0; i < targetT.length; i++) {
      final float tx = tOffset[i];
      final float tv = tVel[i];
      final float tNext = (tx + (tv + omega * tx) * dt) * exp;
      final float tvNext = (tv - omega * (tv + omega * tx) * dt) * exp;
      tOffset[i] = tNext;
      tVel[i] = tvNext;
    }

    for (int i = 0; i < targetS.length; i++) {
      final float sx = sOffset[i];
      final float sv = sVel[i];
      final float sNext = (sx + (sv + omega * sx) * dt) * exp;
      final float svNext = (sv - omega * (sv + omega * sx) * dt) * exp;
      sOffset[i] = sNext;
      sVel[i] = svNext;
    }

    for (int i = 0; i < rOffset.length; i++) {
      final float rx = rOffset[i];
      final float rv = rVel[i];
      final float rNext = (rx + (rv + omega * rx) * dt) * exp;
      final float rvNext = (rv - omega * (rv + omega * rx) * dt) * exp;
      rOffset[i] = rNext;
      rVel[i] = rvNext;
    }

    for (int j = 0; j < outPose.jointCount(); j++) {
      final int tb = j * 3;
      outPose.setTranslation(j, targetT[tb] + tOffset[tb], targetT[tb + 1] + tOffset[tb + 1], targetT[tb + 2] + tOffset[tb + 2]);
      outPose.setScale(j, targetS[tb] + sOffset[tb], targetS[tb + 1] + sOffset[tb + 1], targetS[tb + 2] + sOffset[tb + 2]);

      final int rb = j * 4;
      final int vb = j * 3;
      final float[] delta = quatExp(rOffset[vb], rOffset[vb + 1], rOffset[vb + 2]);
      final float[] out = quatMul(
          new float[] {targetR[rb], targetR[rb + 1], targetR[rb + 2], targetR[rb + 3]},
          delta);
      normalizeInPlace(out);
      outPose.setRotation(j, out[0], out[1], out[2], out[3]);
    }
  }

  private static float[] quatExp(final float vx, final float vy, final float vz) {
    final float angle = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    if (angle <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float half = angle * 0.5f;
    final float s = (float) Math.sin(half) / angle;
    return new float[] {vx * s, vy * s, vz * s, (float) Math.cos(half)};
  }

  private static float[] quatMul(final float[] a, final float[] b) {
    return new float[] {
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    };
  }

  private static void normalizeInPlace(final float[] q) {
    final float lenSq = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3];
    if (lenSq <= 1e-8f) {
      q[0] = 0f;
      q[1] = 0f;
      q[2] = 0f;
      q[3] = 1f;
      return;
    }
    final float inv = 1f / (float) Math.sqrt(lenSq);
    q[0] *= inv;
    q[1] *= inv;
    q[2] *= inv;
    q[3] *= inv;
  }
}
