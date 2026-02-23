package org.animis.runtime.warp;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.animis.warp.WarpTarget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DefaultPoseWarper implements PoseWarper {
  private static final float EPSILON = 1.0e-6f;

  @Override
  public void warp(final PoseBuffer pose, final Skeleton skeleton, final List<WarpTarget> targets) {
    if (targets == null || targets.isEmpty()) {
      return;
    }
    final int jointCount = skeleton.joints().size();
    if (pose.jointCount() < jointCount) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }

    final float[] localT = pose.localTranslations();
    final float[] localR = pose.localRotations();
    final float[] worldP = new float[jointCount * 3];
    final float[] worldQ = new float[jointCount * 4];

    for (final WarpTarget target : targets) {
      if (target == null) {
        continue;
      }
      if (target.joint() < 0 || target.joint() >= jointCount) {
        continue;
      }
      computeWorldTransforms(skeleton, localT, localR, worldP, worldQ);
      applyTranslationWarp(skeleton, target, localT, worldP, worldQ);
      if (target.maxRotationRadians() > EPSILON) {
        computeWorldTransforms(skeleton, localT, localR, worldP, worldQ);
        applyRotationWarp(skeleton, target, localR, worldP, worldQ);
      }
    }
  }

  private static void applyTranslationWarp(
      final Skeleton skeleton,
      final WarpTarget target,
      final float[] localT,
      final float[] worldP,
      final float[] worldQ) {
    final int joint = target.joint();
    final int wp = joint * 3;
    float dx = target.worldPosition()[0] - worldP[wp];
    float dy = target.worldPosition()[1] - worldP[wp + 1];
    float dz = target.worldPosition()[2] - worldP[wp + 2];
    final float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (len < EPSILON) {
      return;
    }
    final float maxT = target.maxTranslationMeters();
    if (maxT <= EPSILON) {
      return;
    }
    if (len > maxT) {
      final float s = maxT / len;
      dx *= s;
      dy *= s;
      dz *= s;
    }

    final List<Integer> chain = chainToRoot(skeleton, joint);
    final int n = chain.size();
    final float sum = n * (n + 1) * 0.5f;
    for (int i = 0; i < n; i++) {
      final int chainJoint = chain.get(i);
      final float weight = (i + 1) / sum;
      float wdx = dx * weight;
      float wdy = dy * weight;
      float wdz = dz * weight;

      final Joint j = skeleton.joints().get(chainJoint);
      if (j.parentIndex() >= 0) {
        final int parentBase = j.parentIndex() * 4;
        final float[] localDelta = rotateByInverse(
            worldQ[parentBase], worldQ[parentBase + 1], worldQ[parentBase + 2], worldQ[parentBase + 3], wdx, wdy, wdz);
        wdx = localDelta[0];
        wdy = localDelta[1];
        wdz = localDelta[2];
      }

      final int tBase = chainJoint * 3;
      localT[tBase] += wdx;
      localT[tBase + 1] += wdy;
      localT[tBase + 2] += wdz;
    }
  }

  private static void applyRotationWarp(
      final Skeleton skeleton,
      final WarpTarget target,
      final float[] localR,
      final float[] worldP,
      final float[] worldQ) {
    final int joint = target.joint();
    final Joint j = skeleton.joints().get(joint);
    final int parent = j.parentIndex();
    if (parent < 0) {
      return;
    }

    final int pb = parent * 3;
    final int jb = joint * 3;
    final float curX = worldP[jb] - worldP[pb];
    final float curY = worldP[jb + 1] - worldP[pb + 1];
    final float curZ = worldP[jb + 2] - worldP[pb + 2];
    final float dstX = target.worldPosition()[0] - worldP[pb];
    final float dstY = target.worldPosition()[1] - worldP[pb + 1];
    final float dstZ = target.worldPosition()[2] - worldP[pb + 2];

    final float[] worldDelta = quatFromTo(curX, curY, curZ, dstX, dstY, dstZ);
    float angle = 2f * (float) Math.acos(clamp(worldDelta[3], -1f, 1f));
    if (angle > Math.PI) {
      angle = (float) (2f * Math.PI - angle);
    }
    if (angle < EPSILON) {
      return;
    }
    final float maxR = target.maxRotationRadians();
    float[] clampedWorldDelta = worldDelta;
    if (angle > maxR) {
      final float t = maxR / angle;
      clampedWorldDelta = quatSlerp(0f, 0f, 0f, 1f, worldDelta[0], worldDelta[1], worldDelta[2], worldDelta[3], t);
    }

    final int pqr = parent * 4;
    final float px = worldQ[pqr];
    final float py = worldQ[pqr + 1];
    final float pz = worldQ[pqr + 2];
    final float pw = worldQ[pqr + 3];
    final float[] localDelta = quatMul(
        quatMul(-px, -py, -pz, pw, clampedWorldDelta[0], clampedWorldDelta[1], clampedWorldDelta[2], clampedWorldDelta[3]),
        px, py, pz, pw);

    final int lr = joint * 4;
    final float[] next = quatMul(localDelta[0], localDelta[1], localDelta[2], localDelta[3], localR[lr], localR[lr + 1], localR[lr + 2], localR[lr + 3]);
    localR[lr] = next[0];
    localR[lr + 1] = next[1];
    localR[lr + 2] = next[2];
    localR[lr + 3] = next[3];
  }

  private static List<Integer> chainToRoot(final Skeleton skeleton, final int joint) {
    final ArrayList<Integer> chain = new ArrayList<>();
    int cursor = joint;
    while (cursor >= 0) {
      chain.add(cursor);
      cursor = skeleton.joints().get(cursor).parentIndex();
    }
    Collections.reverse(chain);
    return chain;
  }

  private static void computeWorldTransforms(
      final Skeleton skeleton,
      final float[] localT,
      final float[] localR,
      final float[] worldP,
      final float[] worldQ) {
    for (final Joint joint : skeleton.joints()) {
      final int i = joint.index();
      final int tb = i * 3;
      final int rb = i * 4;
      final float lx = localT[tb];
      final float ly = localT[tb + 1];
      final float lz = localT[tb + 2];
      final float lqx = localR[rb];
      final float lqy = localR[rb + 1];
      final float lqz = localR[rb + 2];
      final float lqw = localR[rb + 3];

      if (joint.parentIndex() < 0) {
        worldP[tb] = lx;
        worldP[tb + 1] = ly;
        worldP[tb + 2] = lz;
        final float[] n = normalizeQuat(lqx, lqy, lqz, lqw);
        worldQ[rb] = n[0];
        worldQ[rb + 1] = n[1];
        worldQ[rb + 2] = n[2];
        worldQ[rb + 3] = n[3];
        continue;
      }

      final int p = joint.parentIndex();
      final int ptb = p * 3;
      final int prb = p * 4;
      final float px = worldQ[prb];
      final float py = worldQ[prb + 1];
      final float pz = worldQ[prb + 2];
      final float pw = worldQ[prb + 3];
      final float[] rotated = rotateBy(px, py, pz, pw, lx, ly, lz);
      worldP[tb] = worldP[ptb] + rotated[0];
      worldP[tb + 1] = worldP[ptb + 1] + rotated[1];
      worldP[tb + 2] = worldP[ptb + 2] + rotated[2];

      final float[] q = quatMul(px, py, pz, pw, lqx, lqy, lqz, lqw);
      worldQ[rb] = q[0];
      worldQ[rb + 1] = q[1];
      worldQ[rb + 2] = q[2];
      worldQ[rb + 3] = q[3];
    }
  }

  private static float[] rotateBy(final float qx, final float qy, final float qz, final float qw, final float x, final float y, final float z) {
    final float[] t = quatMulRaw(qx, qy, qz, qw, x, y, z, 0f);
    final float[] r = quatMulRaw(t[0], t[1], t[2], t[3], -qx, -qy, -qz, qw);
    return new float[] {r[0], r[1], r[2]};
  }

  private static float[] rotateByInverse(final float qx, final float qy, final float qz, final float qw, final float x, final float y, final float z) {
    return rotateBy(-qx, -qy, -qz, qw, x, y, z);
  }

  private static float[] quatFromTo(
      final float ax,
      final float ay,
      final float az,
      final float bx,
      final float by,
      final float bz) {
    final float[] na = normalizeVec(ax, ay, az);
    final float[] nb = normalizeVec(bx, by, bz);
    final float dot = clamp(na[0] * nb[0] + na[1] * nb[1] + na[2] * nb[2], -1f, 1f);
    if (dot > 1f - EPSILON) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    if (dot < -1f + EPSILON) {
      final float[] axis = orthogonal(na[0], na[1], na[2]);
      return normalizeQuat(axis[0], axis[1], axis[2], 0f);
    }
    final float cx = na[1] * nb[2] - na[2] * nb[1];
    final float cy = na[2] * nb[0] - na[0] * nb[2];
    final float cz = na[0] * nb[1] - na[1] * nb[0];
    final float w = 1f + dot;
    return normalizeQuat(cx, cy, cz, w);
  }

  private static float[] orthogonal(final float x, final float y, final float z) {
    if (Math.abs(x) < Math.abs(y)) {
      return normalizeVec(0f, -z, y);
    }
    return normalizeVec(-z, 0f, x);
  }

  private static float[] normalizeVec(final float x, final float y, final float z) {
    final float len = (float) Math.sqrt(x * x + y * y + z * z);
    if (len < EPSILON) {
      return new float[] {1f, 0f, 0f};
    }
    final float inv = 1f / len;
    return new float[] {x * inv, y * inv, z * inv};
  }

  private static float[] quatMul(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    final float[] raw = quatMulRaw(ax, ay, az, aw, bx, by, bz, bw);
    return normalizeQuat(raw[0], raw[1], raw[2], raw[3]);
  }

  private static float[] quatMul(final float[] a, final float bx, final float by, final float bz, final float bw) {
    return quatMul(a[0], a[1], a[2], a[3], bx, by, bz, bw);
  }

  private static float[] quatMulRaw(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    return new float[] {
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz
    };
  }

  private static float[] quatSlerp(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      float bx,
      float by,
      float bz,
      float bw,
      final float t) {
    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0f) {
      dot = -dot;
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
    }
    if (dot > 0.9995f) {
      return normalizeQuat(
          ax + t * (bx - ax),
          ay + t * (by - ay),
          az + t * (bz - az),
          aw + t * (bw - aw));
    }
    final float theta0 = (float) Math.acos(clamp(dot, -1f, 1f));
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);
    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalizeQuat(s0 * ax + s1 * bx, s0 * ay + s1 * by, s0 * az + s1 * bz, s0 * aw + s1 * bw);
  }

  private static float[] normalizeQuat(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq < EPSILON) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = (float) (1.0 / Math.sqrt(lenSq));
    return new float[] {x * inv, y * inv, z * inv, w * inv};
  }

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }
}
