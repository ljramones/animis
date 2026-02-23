package org.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.blend.ClipNode;
import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.ik.IkChain;
import org.animis.runtime.ik.IkTarget;
import org.animis.runtime.physics.PhysicsCharacterDef;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.animis.state.StateDef;
import org.animis.state.StateMachineDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class RuntimeStressFuzzTest {
  @Test
  void randomPipelineInputs_doNotProduceNaNOrInf() {
    final Random random = new Random(0xA11BEEFL);
    final int jointCount = 36;
    final Skeleton skeleton = chainSkeleton(jointCount);
    final ClipId clipId = new ClipId("stress");
    final Clip clip = randomClip(random, clipId, jointCount, 32, 1f / 30f);
    final StateMachineDef machine = new StateMachineDef(
        "stress-sm",
        List.of(new StateDef("idle", new ClipNode(clipId, 1f), List.of())),
        "idle");
    final IkChain ik = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(clipId, clip),
        Map.of(clipId, true),
        List.of(ik),
        null,
        new PhysicsCharacterDef(0.7f, 0.5f, 20f, List.of(0, 1, 2, 3, 4), List.of(4)));

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.setWarpTarget(new org.animis.warp.WarpTarget("hand", 2, new float[] {1f, 1f, 0f}, 0.25f, 0.2f));

    for (int i = 0; i < 500; i++) {
      final float dt = 1f / (30f + random.nextInt(91)); // [1/120,1/30]
      if ((i & 7) == 0) {
        animator.setIkTarget("arm", IkTarget.withoutPole(
            randomRange(random, -1.5f, 1.5f),
            randomRange(random, -1.5f, 1.5f),
            randomRange(random, -1.5f, 1.5f)));
      }
      animator.update(dt);
      final var pose = animator.pose();
      assertFiniteArray(pose.localTranslations());
      assertFiniteArray(pose.localRotations());
      assertFiniteArray(pose.localScales());
    }
  }

  private static Clip randomClip(
      final Random random,
      final ClipId id,
      final int jointCount,
      final int sampleCount,
      final float sampleInterval) {
    final ArrayList<TransformTrack> tracks = new ArrayList<>(jointCount);
    for (int j = 0; j < jointCount; j++) {
      final float[] t = new float[sampleCount * 3];
      final float[] r = new float[sampleCount * 4];
      final float[] s = new float[sampleCount * 3];
      for (int i = 0; i < sampleCount; i++) {
        final int tb = i * 3;
        t[tb] = randomRange(random, -0.1f, 0.1f);
        t[tb + 1] = randomRange(random, -0.1f, 0.1f);
        t[tb + 2] = randomRange(random, -0.1f, 0.1f);
        s[tb] = randomRange(random, 0.9f, 1.1f);
        s[tb + 1] = randomRange(random, 0.9f, 1.1f);
        s[tb + 2] = randomRange(random, 0.9f, 1.1f);
        final int rb = i * 4;
        final float qx = randomRange(random, -1f, 1f);
        final float qy = randomRange(random, -1f, 1f);
        final float qz = randomRange(random, -1f, 1f);
        final float qw = randomRange(random, -1f, 1f);
        final float inv = (float) (1.0 / Math.sqrt(Math.max(1e-8f, qx * qx + qy * qy + qz * qz + qw * qw)));
        r[rb] = qx * inv;
        r[rb + 1] = qy * inv;
        r[rb + 2] = qz * inv;
        r[rb + 3] = qw * inv;
      }
      final TrackMetadata meta = new TrackMetadata(1f / sampleInterval, CurveTypeHint.SAMPLED, sampleCount, sampleInterval, null);
      tracks.add(new TransformTrack(j, meta, t, r, s));
    }
    return new Clip(id, id.value(), (sampleCount - 1) * sampleInterval, tracks);
  }

  private static Skeleton chainSkeleton(final int jointCount) {
    final ArrayList<Joint> joints = new ArrayList<>(jointCount);
    joints.add(new Joint(0, "j0", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    for (int i = 1; i < jointCount; i++) {
      joints.add(new Joint(i, "j" + i, i - 1, new BindTransform(0.05f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    }
    return new Skeleton("stress-skeleton", joints, 0);
  }

  private static float randomRange(final Random random, final float min, final float max) {
    return min + random.nextFloat() * (max - min);
  }

  private static void assertFiniteArray(final float[] values) {
    for (final float v : values) {
      assertTrue(Float.isFinite(v));
      assertTrue(Math.abs(v) < 1_000_000f);
    }
  }
}
