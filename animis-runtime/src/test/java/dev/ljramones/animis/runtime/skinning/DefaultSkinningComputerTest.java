package dev.ljramones.animis.runtime.skinning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ljramones.animis.runtime.pose.Pose;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultSkinningComputerTest {
  @Test
  void compute_singleJointIdentityProducesIdentitySkinningMatrix() {
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final Pose pose = new Pose(
        new float[] {0f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f},
        1);

    final float[] out = new DefaultSkinningComputer().compute(skeleton, pose).jointMatrices();

    assertEquals(16, out.length);
    assertEquals(1f, out[0], 1e-6f);
    assertEquals(1f, out[5], 1e-6f);
    assertEquals(1f, out[10], 1e-6f);
    assertEquals(1f, out[15], 1e-6f);
    assertEquals(0f, out[12], 1e-6f);
    assertEquals(0f, out[13], 1e-6f);
    assertEquals(0f, out[14], 1e-6f);
  }

  @Test
  void compute_twoJointChainPropagatesParentTransformToChild() {
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "child", 0, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final Pose pose = new Pose(
        new float[] {2f, 0f, 0f, 1f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f},
        2);

    final float[] out = new DefaultSkinningComputer().compute(skeleton, pose).jointMatrices();

    final int childOffset = 16;
    assertEquals(2f, out[childOffset + 12], 1e-6f);
    assertEquals(0f, out[childOffset + 13], 1e-6f);
    assertEquals(0f, out[childOffset + 14], 1e-6f);
  }

  @Test
  void compute_outputMatrixCountMatchesJointCount() {
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "j1", 0, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(2, "j2", 1, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final Pose pose = new Pose(
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f},
        3);

    final float[] out = new DefaultSkinningComputer().compute(skeleton, pose).jointMatrices();

    assertEquals(3 * 16, out.length);
  }
}
