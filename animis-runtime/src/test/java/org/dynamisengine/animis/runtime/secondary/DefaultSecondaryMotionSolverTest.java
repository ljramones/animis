package org.dynamisengine.animis.runtime.secondary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.BindTransform;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.SecondaryChainDef;
import org.dynamisengine.animis.skeleton.Skeleton;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultSecondaryMotionSolverTest {
  @Test
  void solve_clampsAngularDeviationToLimit() {
    final Skeleton skeleton = oneJointSkeleton(0.15f);
    final DefaultSecondaryMotionSolver solver = new DefaultSecondaryMotionSolver();
    final PoseBuffer pose = new PoseBuffer(1);
    pose.reset();

    setYaw(pose, 0f);
    solver.solve(pose, skeleton, 1f / 60f); // initialize state

    setYaw(pose, 1.2f);
    solver.solve(pose, skeleton, 1f / 60f);

    final float deviation = quatDeviationFromYaw(pose.localRotations(), 0, 1.2f);
    assertTrue(deviation <= 0.15f + 1e-3f);
  }

  @Test
  void solve_withZeroDt_doesNothing() {
    final Skeleton skeleton = oneJointSkeleton(0.25f);
    final DefaultSecondaryMotionSolver solver = new DefaultSecondaryMotionSolver();
    final PoseBuffer pose = new PoseBuffer(1);
    pose.reset();
    setYaw(pose, 0.7f);

    solver.solve(pose, skeleton, 0f);

    final float angle = quatAngle(pose.localRotations(), 0);
    assertEquals(0.7f, angle, 1e-4f);
  }

  private static Skeleton oneJointSkeleton(final float angularLimit) {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0,
        List.of(new SecondaryChainDef("tail", List.of(0), 1f, 0.2f, angularLimit, List.of())));
  }

  private static void setYaw(final PoseBuffer pose, final float yawRadians) {
    final float half = yawRadians * 0.5f;
    pose.setRotation(0, 0f, (float) Math.sin(half), 0f, (float) Math.cos(half));
  }

  private static float quatAngle(final float[] rotations, final int jointIndex) {
    final int base = jointIndex * 4;
    final float w = Math.max(-1f, Math.min(1f, rotations[base + 3]));
    float angle = 2f * (float) Math.acos(w);
    if (angle > Math.PI) {
      angle = (float) (2f * Math.PI - angle);
    }
    return angle;
  }

  private static float quatDeviationFromYaw(final float[] rotations, final int jointIndex, final float targetYaw) {
    final int base = jointIndex * 4;
    final float sx = rotations[base];
    final float sy = rotations[base + 1];
    final float sz = rotations[base + 2];
    final float sw = rotations[base + 3];

    final float half = targetYaw * 0.5f;
    final float tx = 0f;
    final float ty = (float) Math.sin(half);
    final float tz = 0f;
    final float tw = (float) Math.cos(half);

    final float dx = -tx;
    final float dy = -ty;
    final float dz = -tz;
    final float dw = tw;

    final float rw = dw * sw - dx * sx - dy * sy - dz * sz;
    float angle = 2f * (float) Math.acos(Math.max(-1f, Math.min(1f, rw)));
    if (angle > Math.PI) {
      angle = (float) (2f * Math.PI - angle);
    }
    return angle;
  }
}
