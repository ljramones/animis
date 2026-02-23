package org.animis.runtime.blend;

import org.animis.blend.BlendLayer;
import org.animis.blend.BoneMask;
import org.animis.blend.LayerMode;
import org.animis.runtime.pose.PoseBuffer;
import java.util.List;

public final class LayeredBlendEvaluator {
  private final BlendEvaluator nodeEvaluator;
  private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

  public LayeredBlendEvaluator(final BlendEvaluator nodeEvaluator) {
    this.nodeEvaluator = nodeEvaluator;
  }

  public void evaluate(final List<BlendLayer> layers, final EvalContext ctx, final PoseBuffer outPose) {
    if (layers == null || layers.isEmpty()) {
      throw new IllegalArgumentException("At least one blend layer is required");
    }

    final Scratch s = this.scratch.get();
    final PoseBuffer basePose = s.base(outPose.jointCount());
    this.nodeEvaluator.evaluate(layers.get(0).root(), ctx, basePose);
    copyPose(basePose, outPose);

    for (int i = 1; i < layers.size(); i++) {
      final BlendLayer layer = layers.get(i);
      final PoseBuffer layerPose = s.layer(outPose.jointCount());
      this.nodeEvaluator.evaluate(layer.root(), ctx, layerPose);
      composeLayer(outPose, layerPose, layer.mask(), layer.mode());
    }
  }

  private static void composeLayer(
      final PoseBuffer basePose,
      final PoseBuffer layerPose,
      final BoneMask mask,
      final LayerMode mode) {
    final float[] bt = basePose.localTranslations();
    final float[] bs = basePose.localScales();
    final float[] br = basePose.localRotations();

    final float[] lt = layerPose.localTranslations();
    final float[] ls = layerPose.localScales();
    final float[] lr = layerPose.localRotations();

    for (int joint = 0; joint < basePose.jointCount(); joint++) {
      final float w = mask == null ? 1f : mask.weight(joint);
      if (w <= 0f) {
        continue;
      }

      final int tb = joint * 3;
      final int rb = joint * 4;

      if (mode == LayerMode.OVERRIDE) {
        basePose.setTranslation(
            joint,
            lerp(bt[tb], lt[tb], w),
            lerp(bt[tb + 1], lt[tb + 1], w),
            lerp(bt[tb + 2], lt[tb + 2], w));
        basePose.setScale(
            joint,
            lerp(bs[tb], ls[tb], w),
            lerp(bs[tb + 1], ls[tb + 1], w),
            lerp(bs[tb + 2], ls[tb + 2], w));

        final float[] q = slerp(
            br[rb], br[rb + 1], br[rb + 2], br[rb + 3],
            lr[rb], lr[rb + 1], lr[rb + 2], lr[rb + 3],
            w);
        basePose.setRotation(joint, q[0], q[1], q[2], q[3]);
      } else {
        basePose.setTranslation(
            joint,
            bt[tb] + lt[tb] * w,
            bt[tb + 1] + lt[tb + 1] * w,
            bt[tb + 2] + lt[tb + 2] * w);
        basePose.setScale(
            joint,
            bs[tb] + (ls[tb] - 1f) * w,
            bs[tb + 1] + (ls[tb + 1] - 1f) * w,
            bs[tb + 2] + (ls[tb + 2] - 1f) * w);

        final float[] weightedAdd = slerp(0f, 0f, 0f, 1f, lr[rb], lr[rb + 1], lr[rb + 2], lr[rb + 3], w);
        final float[] composed = mul(
            br[rb], br[rb + 1], br[rb + 2], br[rb + 3],
            weightedAdd[0], weightedAdd[1], weightedAdd[2], weightedAdd[3]);
        basePose.setRotation(joint, composed[0], composed[1], composed[2], composed[3]);
      }
    }
  }

  private static void copyPose(final PoseBuffer src, final PoseBuffer dst) {
    final float[] st = src.localTranslations();
    final float[] ss = src.localScales();
    final float[] sr = src.localRotations();
    for (int j = 0; j < dst.jointCount(); j++) {
      final int tb = j * 3;
      final int rb = j * 4;
      dst.setTranslation(j, st[tb], st[tb + 1], st[tb + 2]);
      dst.setScale(j, ss[tb], ss[tb + 1], ss[tb + 2]);
      dst.setRotation(j, sr[rb], sr[rb + 1], sr[rb + 2], sr[rb + 3]);
    }
  }

  private static float lerp(final float a, final float b, final float t) {
    return a + (b - a) * t;
  }

  private static float[] mul(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    return normalize(
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz);
  }

  private static float[] slerp(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bxIn,
      final float byIn,
      final float bzIn,
      final float bwIn,
      final float t) {
    float bx = bxIn;
    float by = byIn;
    float bz = bzIn;
    float bw = bwIn;

    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0f) {
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
      dot = -dot;
    }

    if (dot > 0.9995f) {
      return normalize(
          lerp(ax, bx, t),
          lerp(ay, by, t),
          lerp(az, bz, t),
          lerp(aw, bw, t));
    }

    final float theta0 = (float) Math.acos(dot);
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);

    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalize(
        s0 * ax + s1 * bx,
        s0 * ay + s1 * by,
        s0 * az + s1 * bz,
        s0 * aw + s1 * bw);
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 0f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float invLen = 1f / (float) Math.sqrt(lenSq);
    return new float[] {x * invLen, y * invLen, z * invLen, w * invLen};
  }

  private static final class Scratch {
    private PoseBuffer basePose;
    private PoseBuffer layerPose;

    private PoseBuffer base(final int jointCount) {
      if (this.basePose == null || this.basePose.jointCount() != jointCount) {
        this.basePose = new PoseBuffer(jointCount);
      }
      return this.basePose;
    }

    private PoseBuffer layer(final int jointCount) {
      if (this.layerPose == null || this.layerPose.jointCount() != jointCount) {
        this.layerPose = new PoseBuffer(jointCount);
      }
      return this.layerPose;
    }
  }
}
