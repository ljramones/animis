package org.animis.runtime.physics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultPhysicsCharacterControllerTest {
  @Test
  void simulatedPose_convergesTowardTargetOverTime() {
    final Skeleton skeleton = oneJointSkeleton();
    final PoseBuffer start = poseAt(1, 0f, 0f, 0f);
    final PoseBuffer target = poseAt(1, 4f, 0f, 0f);
    final DefaultPhysicsCharacterController controller = new DefaultPhysicsCharacterController(
        new PhysicsCharacterDef(0.9f, 0.35f, 25f, List.of(0), List.of()));
    controller.update(start, skeleton, 1f / 60f);

    float firstError = -1f;
    float finalError = Float.MAX_VALUE;
    for (int i = 0; i < 60; i++) {
      controller.update(target, skeleton, 1f / 60f);
      final PoseBuffer simulated = controller.simulatedPose();
      assertNotNull(simulated);
      final float error = Math.abs(4f - simulated.localTranslations()[0]);
      if (i == 0) {
        firstError = error;
      }
      finalError = error;
    }
    assertTrue(finalError < firstError);
  }

  @Test
  void keyframedJointsRemainUnchanged() {
    final Skeleton skeleton = twoJointSkeleton();
    final PoseBuffer target = poseAt(2, 2f, 0f, 0f);
    target.setTranslation(1, 0.75f, 0f, 0f);

    final DefaultPhysicsCharacterController controller = new DefaultPhysicsCharacterController(
        new PhysicsCharacterDef(0.9f, 0.35f, 25f, List.of(0, 1), List.of(1)));

    for (int i = 0; i < 20; i++) {
      controller.update(target, skeleton, 1f / 60f);
    }
    final PoseBuffer simulated = controller.simulatedPose();
    assertNotNull(simulated);
    assertTrue(Math.abs(simulated.localTranslations()[3] - target.localTranslations()[3]) < 1e-5f);
    assertTrue(Math.abs(simulated.localTranslations()[4] - target.localTranslations()[4]) < 1e-5f);
    assertTrue(Math.abs(simulated.localTranslations()[5] - target.localTranslations()[5]) < 1e-5f);
  }

  @Test
  void stiffnessAffectsConvergenceRate() {
    final Skeleton skeleton = oneJointSkeleton();
    final PoseBuffer start = poseAt(1, 0f, 0f, 0f);
    final PoseBuffer target = poseAt(1, 3f, 0f, 0f);
    final DefaultPhysicsCharacterController fast = new DefaultPhysicsCharacterController(
        new PhysicsCharacterDef(0.95f, 0.8f, 10f, List.of(0), List.of()));
    final DefaultPhysicsCharacterController slow = new DefaultPhysicsCharacterController(
        new PhysicsCharacterDef(0.2f, 0.8f, 10f, List.of(0), List.of()));
    fast.update(start, skeleton, 1f / 60f);
    slow.update(start, skeleton, 1f / 60f);

    for (int i = 0; i < 8; i++) {
      fast.update(target, skeleton, 1f / 60f);
      slow.update(target, skeleton, 1f / 60f);
    }

    final float fastError = Math.abs(3f - fast.simulatedPose().localTranslations()[0]);
    final float slowError = Math.abs(3f - slow.simulatedPose().localTranslations()[0]);
    assertTrue(Math.abs(fastError - slowError) > 1e-3f);
  }

  private static PoseBuffer poseAt(final int jointCount, final float x, final float y, final float z) {
    final PoseBuffer pose = new PoseBuffer(jointCount);
    for (int i = 0; i < jointCount; i++) {
      pose.setTranslation(i, i == 0 ? x : 0.5f, i == 0 ? y : 0f, i == 0 ? z : 0f);
      pose.setRotation(i, 0f, 0f, 0f, 1f);
      pose.setScale(i, 1f, 1f, 1f);
    }
    return pose;
  }

  private static Skeleton oneJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Skeleton twoJointSkeleton() {
    return new Skeleton(
        "s2",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "child", 0, new BindTransform(0.5f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }
}
