package org.animis.runtime.ik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.ik.FabrikChainDef;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FabrikSolverTest {
  @Test
  void solve_reachesTargetWithinTolerance() {
    final Skeleton skeleton = linearSkeleton(4, 1f);
    final PoseBuffer pose = identityPose(4, 1f);
    final FabrikChainDef chain = new FabrikChainDef("spine", List.of(0, 1, 2, 3), 0.01f, 20, List.of());

    new FabrikSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(2f, 1f, 0f));

    final Vec3 tip = worldPosition(skeleton, pose, 3);
    final float dist = tip.sub(new Vec3(2f, 1f, 0f)).length();
    assertTrue(dist <= 0.02f);
  }

  @Test
  void solve_respectsMaxIterations() {
    final Skeleton skeleton = linearSkeleton(4, 1f);
    final PoseBuffer pose = identityPose(4, 1f);
    final FabrikChainDef chain = new FabrikChainDef("spine", List.of(0, 1, 2, 3), 1e-5f, 1, List.of());

    new FabrikSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(2f, 2f, 0f));

    final Vec3 tip = worldPosition(skeleton, pose, 3);
    final float dist = tip.sub(new Vec3(2f, 2f, 0f)).length();
    assertTrue(dist > chain.tolerance());
  }

  @Test
  void solve_appliesAngleLimits() {
    final Skeleton skeleton = linearSkeleton(3, 1f);
    final PoseBuffer limitedPose = identityPose(3, 1f);
    final PoseBuffer unrestrictedPose = identityPose(3, 1f);
    final float maxAngle = 0.35f;
    final FabrikChainDef limitedChain = new FabrikChainDef(
        "tail",
        List.of(0, 1, 2),
        0.001f,
        15,
        List.of(new float[] {0f, (float) Math.PI}, new float[] {0f, maxAngle}));
    final FabrikChainDef unrestrictedChain = new FabrikChainDef(
        "tail-unrestricted",
        List.of(0, 1, 2),
        0.001f,
        15,
        List.of());

    final FabrikSolver solver = new FabrikSolver();
    solver.solve(limitedPose, skeleton, limitedChain, IkTarget.withoutPole(1f, 1f, 0f));
    solver.solve(unrestrictedPose, skeleton, unrestrictedChain, IkTarget.withoutPole(1f, 1f, 0f));

    final Vec3 lRoot = worldPosition(skeleton, limitedPose, 0);
    final Vec3 lMid = worldPosition(skeleton, limitedPose, 1);
    final Vec3 lTip = worldPosition(skeleton, limitedPose, 2);
    final float included = angleBetween(lRoot.sub(lMid), lTip.sub(lMid));
    final float bend = (float) Math.PI - included;
    assertTrue(bend <= maxAngle + 0.05f);
  }

  @Test
  void solve_singleJointChainMovesRootToTarget() {
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final PoseBuffer pose = new PoseBuffer(1);
    pose.setTranslation(0, 0f, 0f, 0f);
    pose.setRotation(0, 0f, 0f, 0f, 1f);
    pose.setScale(0, 1f, 1f, 1f);
    final FabrikChainDef chain = new FabrikChainDef("single", List.of(0), 0.001f, 10, List.of());

    new FabrikSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(3f, -2f, 1f));

    assertEquals(3f, pose.localTranslations()[0], 1e-4f);
    assertEquals(-2f, pose.localTranslations()[1], 1e-4f);
    assertEquals(1f, pose.localTranslations()[2], 1e-4f);
  }

  private static Skeleton linearSkeleton(final int jointCount, final float segment) {
    final java.util.ArrayList<Joint> joints = new java.util.ArrayList<>();
    joints.add(new Joint(0, "j0", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    for (int i = 1; i < jointCount; i++) {
      joints.add(new Joint(i, "j" + i, i - 1, new BindTransform(segment, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    }
    return new Skeleton("s", joints, 0);
  }

  private static PoseBuffer identityPose(final int jointCount, final float segment) {
    final PoseBuffer pose = new PoseBuffer(jointCount);
    for (int i = 0; i < jointCount; i++) {
      if (i == 0) {
        pose.setTranslation(i, 0f, 0f, 0f);
      } else {
        pose.setTranslation(i, segment, 0f, 0f);
      }
      pose.setRotation(i, 0f, 0f, 0f, 1f);
      pose.setScale(i, 1f, 1f, 1f);
    }
    return pose;
  }

  private static Vec3 worldPosition(final Skeleton skeleton, final PoseBuffer pose, final int jointIndex) {
    final int count = skeleton.joints().size();
    final float[] wx = new float[count];
    final float[] wy = new float[count];
    final float[] wz = new float[count];
    final Quat[] wr = new Quat[count];
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

  private static float angleBetween(final Vec3 a, final Vec3 b) {
    final float dot = a.dot(b);
    final float len = a.length() * b.length();
    if (len <= 1e-6f) {
      return 0f;
    }
    return (float) Math.acos(Math.max(-1f, Math.min(1f, dot / len)));
  }

  private record Vec3(float x, float y, float z) {
    private Vec3 add(final Vec3 o) {
      return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
    }

    private Vec3 sub(final Vec3 o) {
      return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z);
    }

    private float dot(final Vec3 o) {
      return this.x * o.x + this.y * o.y + this.z * o.z;
    }

    private float length() {
      return (float) Math.sqrt(this.dot(this));
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
