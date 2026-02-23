package dev.ljramones.animis.runtime.skinning;

import dev.ljramones.animis.runtime.pose.Pose;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultSkinningComputer implements SkinningComputer {
  private static final int MAT_SIZE = 16;
  private final Map<Skeleton, SkeletonCache> cacheBySkeleton = new ConcurrentHashMap<>();

  @Override
  public SkinningOutput compute(final Skeleton skeleton, final Pose pose) {
    final int jointCount = skeleton.joints().size();
    if (pose.jointCount() < jointCount) {
      throw new IllegalArgumentException("Pose jointCount is smaller than skeleton joint count");
    }

    final SkeletonCache cache = this.cacheBySkeleton.computeIfAbsent(skeleton, DefaultSkinningComputer::buildCache);
    final float[] world = new float[jointCount * MAT_SIZE];
    final float[] out = new float[jointCount * MAT_SIZE];
    final float[] lt = pose.localTranslations();
    final float[] lr = pose.localRotations();
    final float[] ls = pose.localScales();

    for (final Joint joint : skeleton.joints()) {
      final int index = joint.index();
      final int offset = index * MAT_SIZE;
      final float[] local = matrixFromTrs(
          lt[index * 3], lt[index * 3 + 1], lt[index * 3 + 2],
          lr[index * 4], lr[index * 4 + 1], lr[index * 4 + 2], lr[index * 4 + 3],
          ls[index * 3], ls[index * 3 + 1], ls[index * 3 + 2]);

      if (joint.parentIndex() < 0) {
        System.arraycopy(local, 0, world, offset, MAT_SIZE);
      } else {
        final int parentOffset = joint.parentIndex() * MAT_SIZE;
        final float[] combined = mul(slice(world, parentOffset), local);
        System.arraycopy(combined, 0, world, offset, MAT_SIZE);
      }
    }

    for (int i = 0; i < jointCount; i++) {
      final int offset = i * MAT_SIZE;
      final float[] skin = mul(slice(world, offset), cache.inverseBindMatrices[i]);
      System.arraycopy(skin, 0, out, offset, MAT_SIZE);
    }
    return new DefaultSkinningOutput(out);
  }

  private static SkeletonCache buildCache(final Skeleton skeleton) {
    final int jointCount = skeleton.joints().size();
    final float[] bindWorld = new float[jointCount * MAT_SIZE];
    final float[][] inverseBind = new float[jointCount][];

    for (final Joint joint : skeleton.joints()) {
      final int index = joint.index();
      final int offset = index * MAT_SIZE;
      final BindTransform bind = joint.bind();
      final float[] local = matrixFromTrs(
          bind.tx(), bind.ty(), bind.tz(),
          bind.qx(), bind.qy(), bind.qz(), bind.qw(),
          bind.sx(), bind.sy(), bind.sz());

      if (joint.parentIndex() < 0) {
        System.arraycopy(local, 0, bindWorld, offset, MAT_SIZE);
      } else {
        final int parentOffset = joint.parentIndex() * MAT_SIZE;
        final float[] combined = mul(slice(bindWorld, parentOffset), local);
        System.arraycopy(combined, 0, bindWorld, offset, MAT_SIZE);
      }
    }

    for (int i = 0; i < jointCount; i++) {
      inverseBind[i] = invertTrs(slice(bindWorld, i * MAT_SIZE));
    }
    return new SkeletonCache(inverseBind);
  }

  private static float[] matrixFromTrs(
      final float tx,
      final float ty,
      final float tz,
      final float qx,
      final float qy,
      final float qz,
      final float qwIn,
      final float sx,
      final float sy,
      final float sz) {
    final float lenSq = qx * qx + qy * qy + qz * qz + qwIn * qwIn;
    final float invLen = lenSq <= 1e-8f ? 1f : 1f / (float) Math.sqrt(lenSq);
    final float x = qx * invLen;
    final float y = qy * invLen;
    final float z = qz * invLen;
    final float w = qwIn * invLen;

    final float xx = x * x;
    final float yy = y * y;
    final float zz = z * z;
    final float xy = x * y;
    final float xz = x * z;
    final float yz = y * z;
    final float wx = w * x;
    final float wy = w * y;
    final float wz = w * z;

    final float m00 = (1f - 2f * (yy + zz)) * sx;
    final float m01 = (2f * (xy + wz)) * sx;
    final float m02 = (2f * (xz - wy)) * sx;
    final float m10 = (2f * (xy - wz)) * sy;
    final float m11 = (1f - 2f * (xx + zz)) * sy;
    final float m12 = (2f * (yz + wx)) * sy;
    final float m20 = (2f * (xz + wy)) * sz;
    final float m21 = (2f * (yz - wx)) * sz;
    final float m22 = (1f - 2f * (xx + yy)) * sz;

    return new float[] {
        m00, m10, m20, 0f,
        m01, m11, m21, 0f,
        m02, m12, m22, 0f,
        tx, ty, tz, 1f
    };
  }

  private static float[] mul(final float[] a, final float[] b) {
    final float[] out = new float[MAT_SIZE];
    for (int col = 0; col < 4; col++) {
      for (int row = 0; row < 4; row++) {
        out[col * 4 + row] =
            a[row] * b[col * 4]
                + a[4 + row] * b[col * 4 + 1]
                + a[8 + row] * b[col * 4 + 2]
                + a[12 + row] * b[col * 4 + 3];
      }
    }
    return out;
  }

  private static float[] invertTrs(final float[] m) {
    final float tx = m[12];
    final float ty = m[13];
    final float tz = m[14];

    final float[] c0 = new float[] {m[0], m[1], m[2]};
    final float[] c1 = new float[] {m[4], m[5], m[6]};
    final float[] c2 = new float[] {m[8], m[9], m[10]};

    final float invSx = safeInv(length(c0));
    final float invSy = safeInv(length(c1));
    final float invSz = safeInv(length(c2));

    final float[] r0 = scale(c0, invSx);
    final float[] r1 = scale(c1, invSy);
    final float[] r2 = scale(c2, invSz);

    final float[] inv = new float[MAT_SIZE];
    inv[0] = r0[0] * invSx;
    inv[1] = r0[1] * invSx;
    inv[2] = r0[2] * invSx;
    inv[3] = 0f;
    inv[4] = r1[0] * invSy;
    inv[5] = r1[1] * invSy;
    inv[6] = r1[2] * invSy;
    inv[7] = 0f;
    inv[8] = r2[0] * invSz;
    inv[9] = r2[1] * invSz;
    inv[10] = r2[2] * invSz;
    inv[11] = 0f;
    inv[12] = -(inv[0] * tx + inv[4] * ty + inv[8] * tz);
    inv[13] = -(inv[1] * tx + inv[5] * ty + inv[9] * tz);
    inv[14] = -(inv[2] * tx + inv[6] * ty + inv[10] * tz);
    inv[15] = 1f;
    return inv;
  }

  private static float safeInv(final float v) {
    return Math.abs(v) <= 1e-8f ? 0f : 1f / v;
  }

  private static float[] scale(final float[] v, final float s) {
    return new float[] {v[0] * s, v[1] * s, v[2] * s};
  }

  private static float length(final float[] v) {
    return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
  }

  private static float[] slice(final float[] data, final int offset) {
    return Arrays.copyOfRange(data, offset, offset + MAT_SIZE);
  }

  private static final class SkeletonCache {
    private final float[][] inverseBindMatrices;

    private SkeletonCache(final float[][] inverseBindMatrices) {
      this.inverseBindMatrices = inverseBindMatrices;
    }
  }

  private static final class DefaultSkinningOutput implements SkinningOutput {
    private final float[] jointMatrices;

    private DefaultSkinningOutput(final float[] jointMatrices) {
      this.jointMatrices = Arrays.copyOf(jointMatrices, jointMatrices.length);
    }

    @Override
    public float[] jointMatrices() {
      return Arrays.copyOf(this.jointMatrices, this.jointMatrices.length);
    }
  }
}
