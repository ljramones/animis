package dev.ljramones.animis.runtime.ik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ljramones.animis.ik.IkChain;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TwoBoneIkSolverTest {
  @Test
  void solve_reachesTargetAtExactDistance() {
    final Skeleton skeleton = threeJointSkeleton(1f, 1f);
    final PoseBuffer pose = identityPose(3, 1f, 1f);
    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    new TwoBoneIkSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(1f, 1f, 0f));

    final Vec3 tip = worldPosition(skeleton, pose, 2);
    assertEquals(1f, tip.x, 1e-3f);
    assertEquals(1f, tip.y, 1e-3f);
    assertEquals(0f, tip.z, 1e-3f);
  }

  @Test
  void solve_clampsToMaxStretchWhenTargetTooFar() {
    final Skeleton skeleton = threeJointSkeleton(1f, 1f);
    final PoseBuffer pose = identityPose(3, 1f, 1f);
    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    new TwoBoneIkSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(5f, 0f, 0f));

    final Vec3 tip = worldPosition(skeleton, pose, 2);
    assertEquals(2f, tip.x, 1e-3f);
    assertEquals(0f, tip.y, 1e-3f);
    assertEquals(0f, tip.z, 1e-3f);
  }

  @Test
  void solve_usesPoleTargetToDeflectBendPlane() {
    final Skeleton skeleton = threeJointSkeleton(1f, 1f);
    final PoseBuffer pose = identityPose(3, 1f, 1f);
    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    new TwoBoneIkSolver().solve(pose, skeleton, chain, IkTarget.withPole(1.5f, 0f, 0f, 0f, 0f, 1f));

    final Vec3 mid = worldPosition(skeleton, pose, 1);
    assertTrue(mid.z > 0f);
  }

  @Test
  void solve_returnsForZeroLengthChain() {
    final Skeleton skeleton = threeJointSkeleton(0f, 1f);
    final PoseBuffer pose = identityPose(3, 0f, 1f);
    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    new TwoBoneIkSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(1f, 1f, 0f));

    assertEquals(0f, pose.localRotations()[0], 1e-6f);
    assertEquals(0f, pose.localRotations()[1], 1e-6f);
    assertEquals(0f, pose.localRotations()[2], 1e-6f);
    assertEquals(1f, pose.localRotations()[3], 1e-6f);

    assertEquals(0f, pose.localRotations()[4], 1e-6f);
    assertEquals(0f, pose.localRotations()[5], 1e-6f);
    assertEquals(0f, pose.localRotations()[6], 1e-6f);
    assertEquals(1f, pose.localRotations()[7], 1e-6f);
  }

  private static Skeleton threeJointSkeleton(final float upperLen, final float lowerLen) {
    return new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "mid", 0, new BindTransform(upperLen, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(2, "tip", 1, new BindTransform(lowerLen, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static PoseBuffer identityPose(final int jointCount, final float upperLen, final float lowerLen) {
    final PoseBuffer pose = new PoseBuffer(jointCount);
    pose.setTranslation(0, 0f, 0f, 0f);
    pose.setTranslation(1, upperLen, 0f, 0f);
    pose.setTranslation(2, lowerLen, 0f, 0f);
    pose.setRotation(0, 0f, 0f, 0f, 1f);
    pose.setRotation(1, 0f, 0f, 0f, 1f);
    pose.setRotation(2, 0f, 0f, 0f, 1f);
    pose.setScale(0, 1f, 1f, 1f);
    pose.setScale(1, 1f, 1f, 1f);
    pose.setScale(2, 1f, 1f, 1f);
    return pose;
  }

  private static Vec3 worldPosition(final Skeleton skeleton, final PoseBuffer pose, final int jointIndex) {
    final float[] wx = new float[skeleton.joints().size()];
    final float[] wy = new float[skeleton.joints().size()];
    final float[] wz = new float[skeleton.joints().size()];
    final Quat[] wr = new Quat[skeleton.joints().size()];

    for (final Joint joint : skeleton.joints()) {
      final int i = joint.index();
      final int tb = i * 3;
      final int rb = i * 4;
      final Vec3 lt = new Vec3(pose.localTranslations()[tb], pose.localTranslations()[tb + 1], pose.localTranslations()[tb + 2]);
      final Quat lr = new Quat(pose.localRotations()[rb], pose.localRotations()[rb + 1], pose.localRotations()[rb + 2], pose.localRotations()[rb + 3]).normalized();
      if (joint.parentIndex() < 0) {
        wx[i] = lt.x;
        wy[i] = lt.y;
        wz[i] = lt.z;
        wr[i] = lr;
      } else {
        final int p = joint.parentIndex();
        final Vec3 wt = new Vec3(wx[p], wy[p], wz[p]).add(wr[p].rotate(lt));
        wx[i] = wt.x;
        wy[i] = wt.y;
        wz[i] = wt.z;
        wr[i] = wr[p].mul(lr).normalized();
      }
    }
    return new Vec3(wx[jointIndex], wy[jointIndex], wz[jointIndex]);
  }

  private record Vec3(float x, float y, float z) {
    private Vec3 add(final Vec3 o) {
      return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
    }
  }

  private record Quat(float x, float y, float z, float w) {
    private Quat normalized() {
      final float lenSq = this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w;
      if (lenSq <= 1e-6f) {
        return new Quat(0f, 0f, 0f, 1f);
      }
      final float inv = 1f / (float) Math.sqrt(lenSq);
      return new Quat(this.x * inv, this.y * inv, this.z * inv, this.w * inv);
    }

    private Quat mul(final Quat o) {
      return new Quat(
          this.w * o.x + this.x * o.w + this.y * o.z - this.z * o.y,
          this.w * o.y - this.x * o.z + this.y * o.w + this.z * o.x,
          this.w * o.z + this.x * o.y - this.y * o.x + this.z * o.w,
          this.w * o.w - this.x * o.x - this.y * o.y - this.z * o.z);
    }

    private Vec3 rotate(final Vec3 v) {
      final Quat qv = new Quat(v.x, v.y, v.z, 0f);
      final Quat inv = new Quat(-this.x, -this.y, -this.z, this.w).normalized();
      final Quat out = this.mul(qv).mul(inv);
      return new Vec3(out.x, out.y, out.z);
    }
  }
}
