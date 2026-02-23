package org.animis.runtime.state;

import org.animis.runtime.pose.PoseBuffer;

final class InertialState {
  private final float[] translationOffsets;
  private final float[] translationVelocities;
  private final float[] scaleOffsets;
  private final float[] scaleVelocities;
  private final float[] rotationOffsets;
  private final float[] rotationVelocities;

  private InertialState(
      final float[] translationOffsets,
      final float[] translationVelocities,
      final float[] scaleOffsets,
      final float[] scaleVelocities,
      final float[] rotationOffsets,
      final float[] rotationVelocities) {
    this.translationOffsets = translationOffsets;
    this.translationVelocities = translationVelocities;
    this.scaleOffsets = scaleOffsets;
    this.scaleVelocities = scaleVelocities;
    this.rotationOffsets = rotationOffsets;
    this.rotationVelocities = rotationVelocities;
  }

  static InertialState capture(
      final PoseBuffer source,
      final PoseBuffer target,
      final float[] prevTranslations,
      final float[] prevRotations,
      final float[] prevScales,
      final float prevDt) {
    final int jointCount = source.jointCount();
    final float[] sourceT = source.localTranslations();
    final float[] sourceR = source.localRotations();
    final float[] sourceS = source.localScales();
    final float[] targetT = target.localTranslations();
    final float[] targetR = target.localRotations();
    final float[] targetS = target.localScales();

    final float[] tOffset = new float[sourceT.length];
    final float[] tVelocity = new float[sourceT.length];
    final float[] sOffset = new float[sourceS.length];
    final float[] sVelocity = new float[sourceS.length];
    final float[] rOffset = new float[jointCount * 3];
    final float[] rVelocity = new float[jointCount * 3];

    final boolean hasPrev = prevTranslations != null && prevRotations != null && prevScales != null && prevDt > 1e-6f;
    for (int i = 0; i < sourceT.length; i++) {
      tOffset[i] = sourceT[i] - targetT[i];
      sOffset[i] = sourceS[i] - targetS[i];
      if (hasPrev) {
        final float prevOffsetT = prevTranslations[i] - targetT[i];
        tVelocity[i] = (tOffset[i] - prevOffsetT) / prevDt;

        final float prevOffsetS = prevScales[i] - targetS[i];
        sVelocity[i] = (sOffset[i] - prevOffsetS) / prevDt;
      }
    }

    for (int j = 0; j < jointCount; j++) {
      final int rb = j * 4;
      final float[] qTargetInv = quatInverse(targetR[rb], targetR[rb + 1], targetR[rb + 2], targetR[rb + 3]);
      final float[] delta = quatMul(qTargetInv, new float[] {sourceR[rb], sourceR[rb + 1], sourceR[rb + 2], sourceR[rb + 3]});
      final float[] rv = quatLog(delta[0], delta[1], delta[2], delta[3]);
      final int vb = j * 3;
      rOffset[vb] = rv[0];
      rOffset[vb + 1] = rv[1];
      rOffset[vb + 2] = rv[2];

      if (hasPrev) {
        final float[] prevDelta = quatMul(qTargetInv, new float[] {prevRotations[rb], prevRotations[rb + 1], prevRotations[rb + 2], prevRotations[rb + 3]});
        final float[] prevRv = quatLog(prevDelta[0], prevDelta[1], prevDelta[2], prevDelta[3]);
        rVelocity[vb] = (rv[0] - prevRv[0]) / prevDt;
        rVelocity[vb + 1] = (rv[1] - prevRv[1]) / prevDt;
        rVelocity[vb + 2] = (rv[2] - prevRv[2]) / prevDt;
      }
    }

    return new InertialState(tOffset, tVelocity, sOffset, sVelocity, rOffset, rVelocity);
  }

  float[] translationOffsets() {
    return this.translationOffsets;
  }

  float[] translationVelocities() {
    return this.translationVelocities;
  }

  float[] scaleOffsets() {
    return this.scaleOffsets;
  }

  float[] scaleVelocities() {
    return this.scaleVelocities;
  }

  float[] rotationOffsets() {
    return this.rotationOffsets;
  }

  float[] rotationVelocities() {
    return this.rotationVelocities;
  }

  private static float[] quatMul(final float[] a, final float[] b) {
    return new float[] {
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    };
  }

  private static float[] quatInverse(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = 1f / lenSq;
    return new float[] {-x * inv, -y * inv, -z * inv, w * inv};
  }

  private static float[] quatLog(final float x, final float y, final float z, final float wIn) {
    final float len = (float) Math.sqrt(x * x + y * y + z * z + wIn * wIn);
    final float w = len <= 1e-8f ? 1f : wIn / len;
    final float vx = len <= 1e-8f ? 0f : x / len;
    final float vy = len <= 1e-8f ? 0f : y / len;
    final float vz = len <= 1e-8f ? 0f : z / len;
    final float sinHalf = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    if (sinHalf <= 1e-8f) {
      return new float[] {0f, 0f, 0f};
    }
    final float halfAngle = (float) Math.atan2(sinHalf, w);
    final float angle = 2f * halfAngle;
    final float scale = angle / sinHalf;
    return new float[] {vx * scale, vy * scale, vz * scale};
  }
}
