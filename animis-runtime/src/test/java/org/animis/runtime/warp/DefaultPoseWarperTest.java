package org.animis.runtime.warp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.animis.warp.WarpTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultPoseWarperTest {
  @Test
  void warp_movesJointTowardTargetWithinClamp() {
    final Skeleton skeleton = twoJointSkeleton();
    final PoseBuffer pose = identityPose(2);
    final PoseWarper warper = new DefaultPoseWarper();
    final float[] before = worldPosition(skeleton, pose, 1);

    warper.warp(
        pose,
        skeleton,
        List.of(new WarpTarget("hand", 1, new float[] {2f, 0f, 0f}, 1f, 0.2f)));

    final float[] after = worldPosition(skeleton, pose, 1);
    final float moved = distance(before, after);
    assertTrue(moved <= 1f + 1e-4f);
    assertTrue(distance(after, new float[] {2f, 0f, 0f}) < distance(before, new float[] {2f, 0f, 0f}));
  }

  @Test
  void warp_clampsTranslationAndPreventsOvershoot() {
    final Skeleton skeleton = twoJointSkeleton();
    final PoseBuffer pose = identityPose(2);
    final PoseWarper warper = new DefaultPoseWarper();
    final float[] before = worldPosition(skeleton, pose, 1);

    warper.warp(
        pose,
        skeleton,
        List.of(new WarpTarget("hand", 1, new float[] {10f, 0f, 0f}, 0.5f, 0.1f)));

    final float[] after = worldPosition(skeleton, pose, 1);
    assertTrue(distance(before, after) <= 0.5f + 1e-4f);
  }

  @Test
  void warp_multipleTargetsAreApplied() {
    final Skeleton skeleton = branchingSkeleton();
    final PoseBuffer pose = identityPose(3);
    final PoseWarper warper = new DefaultPoseWarper();
    final float[] beforeA = worldPosition(skeleton, pose, 1);
    final float[] beforeB = worldPosition(skeleton, pose, 2);

    final float[] targetA = new float[] {1f, 0f, 0f};
    final float[] targetB = new float[] {0f, 0f, 1f};
    warper.warp(
        pose,
        skeleton,
        List.of(
            new WarpTarget("a", 1, targetA, 0.7f, 0.2f),
            new WarpTarget("b", 2, targetB, 0.7f, 0.2f)));

    final float[] afterA = worldPosition(skeleton, pose, 1);
    final float[] afterB = worldPosition(skeleton, pose, 2);
    assertTrue(distance(afterA, targetA) < distance(beforeA, targetA));
    assertTrue(distance(afterB, targetB) < distance(beforeB, targetB));
  }

  private static Skeleton twoJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "hand", 0, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Skeleton branchingSkeleton() {
    return new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "a", 0, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(2, "b", 0, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static PoseBuffer identityPose(final int jointCount) {
    final PoseBuffer pose = new PoseBuffer(jointCount);
    pose.reset();
    return pose;
  }

  private static float[] worldPosition(final Skeleton skeleton, final PoseBuffer pose, final int joint) {
    final float[] t = pose.localTranslations();
    final int parent = skeleton.joints().get(joint).parentIndex();
    final int b = joint * 3;
    if (parent < 0) {
      return new float[] {t[b], t[b + 1], t[b + 2]};
    }
    final int p = parent * 3;
    return new float[] {t[p] + t[b], t[p + 1] + t[b + 1], t[p + 2] + t[b + 2]};
  }

  private static float distance(final float[] a, final float[] b) {
    final float dx = a[0] - b[0];
    final float dy = a[1] - b[1];
    final float dz = a[2] - b[2];
    return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
  }
}
