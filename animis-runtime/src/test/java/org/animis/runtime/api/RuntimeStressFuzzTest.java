package org.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.blend.ClipNode;
import org.animis.blend.LerpNode;
import org.animis.blend.OneDChild;
import org.animis.blend.OneDNode;
import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.ik.FabrikChainDef;
import org.animis.ik.IkChain;
import org.animis.motion.MotionDatabase;
import org.animis.motion.MotionFeatureSchema;
import org.animis.motion.MotionFrame;
import org.animis.runtime.ik.FabrikSolver;
import org.animis.runtime.ik.IkTarget;
import org.animis.runtime.ik.TwoBoneIkSolver;
import org.animis.runtime.physics.DefaultPhysicsCharacterController;
import org.animis.runtime.physics.PhysicsCharacterDef;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.runtime.secondary.DefaultSecondaryMotionSolver;
import org.animis.runtime.skinning.DefaultSkinningComputer;
import org.animis.runtime.warp.DefaultPoseWarper;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.SecondaryChainDef;
import org.animis.skeleton.Skeleton;
import org.animis.state.StateDef;
import org.animis.state.StateMachineDef;
import org.animis.state.TransitionDef;
import org.animis.warp.WarpTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class RuntimeStressFuzzTest {
  @Test
  void zeroJointSkeleton_throwsClearMessage() {
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new Skeleton("zero", List.of(), 0));
    assertTrue(ex.getMessage().contains("rootJoint"));
  }

  @Test
  void singleJointSkeleton_fullPipelineRemainsValid() {
    final Skeleton skeleton = oneJointSkeleton(0.25f, -0.1f, 0.75f);
    final Clip clip = constantClip("idle", 0, 1.25f);
    final AnimatorInstance animator = animatorFor(
        skeleton,
        new StateDef("idle", new ClipNode(clip.id(), 1f), List.of()),
        Map.of(clip.id(), clip),
        Map.of(clip.id(), true));

    animator.update(1f / 60f);
    assertFinitePose(animator.pose().localTranslations(), animator.pose().localRotations(), animator.pose().localScales());
    assertUnitQuaternions(animator.pose().localRotations());
    assertEquals(16, animator.skinningOutput().jointMatrices().length);
  }

  @Test
  void zeroDurationClip_doesNotDivideByZero() {
    final Skeleton skeleton = chainSkeleton(2);
    final ClipId clipId = new ClipId("zero-duration");
    final TrackMetadata metadata = new TrackMetadata(30f, CurveTypeHint.SAMPLED, 1, 0f, null);
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {0f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    final Clip clip = new Clip(clipId, "zero-duration", 0f, List.of(track));

    final AnimatorInstance animator = animatorFor(
        skeleton,
        new StateDef("idle", new ClipNode(clipId, 1f), List.of()),
        Map.of(clipId, clip),
        Map.of(clipId, true));

    animator.update(0.25f);
    assertFinitePose(animator.pose().localTranslations(), animator.pose().localRotations(), animator.pose().localScales());
    assertUnitQuaternions(animator.pose().localRotations());
  }

  @Test
  void clipWithNoTracks_outputsBindPose() {
    final Skeleton skeleton = new Skeleton(
        "bind",
        List.of(
            new Joint(0, "root", -1, new BindTransform(1f, 2f, 3f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "child", 0, new BindTransform(4f, 5f, 6f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final ClipId clipId = new ClipId("empty");
    final Clip clip = new Clip(clipId, "empty", 1f, List.of());

    final AnimatorInstance animator = animatorFor(
        skeleton,
        new StateDef("idle", new ClipNode(clipId, 1f), List.of()),
        Map.of(clipId, clip),
        Map.of(clipId, true));
    animator.update(0.1f);

    final float[] t = animator.pose().localTranslations();
    assertArrayEquals(new float[] {1f, 2f, 3f, 4f, 5f, 6f}, t, 1e-6f);
  }

  @Test
  void emptyBlendTree_nullRoot_throwsClearError() {
    final Skeleton skeleton = chainSkeleton(2);
    final Clip clip = constantClip("idle", 0, 0f);
    final StateDef invalidState = new StateDef("idle", null, List.of());

    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> animatorFor(skeleton, invalidState, Map.of(clip.id(), clip), Map.of(clip.id(), true)));
    assertTrue(ex.getMessage().contains("BlendNode"));
  }

  @Test
  void lerpNode_parameterOutsideRange_clampsToEndpoints() {
    final Skeleton skeleton = oneJointSkeleton(0f, 0f, 0f);
    final Clip a = constantClip("a", 0, 0f);
    final Clip b = constantClip("b", 0, 10f);
    final StateDef state = new StateDef("idle", new LerpNode(new ClipNode(a.id(), 1f), new ClipNode(b.id(), 1f), "blend"), List.of());
    final AnimatorInstance animator = animatorFor(
        skeleton,
        state,
        Map.of(a.id(), a, b.id(), b),
        Map.of(a.id(), true, b.id(), true));

    animator.setFloat("blend", 2f);
    animator.update(0f);
    assertEquals(10f, animator.pose().localTranslations()[0], 1e-6f);

    animator.setFloat("blend", -3f);
    animator.update(0f);
    assertEquals(0f, animator.pose().localTranslations()[0], 1e-6f);
  }

  @Test
  void oneDNode_singleChild_noCrash() {
    final Skeleton skeleton = oneJointSkeleton(0f, 0f, 0f);
    final Clip clip = constantClip("walk", 0, 2f);
    final OneDNode node = new OneDNode("speed", List.of(new OneDChild(0f, new ClipNode(clip.id(), 1f))));
    final AnimatorInstance animator = animatorFor(
        skeleton,
        new StateDef("idle", node, List.of()),
        Map.of(clip.id(), clip),
        Map.of(clip.id(), true));

    animator.setFloat("speed", 100f);
    animator.update(0.016f);
    assertFinitePose(animator.pose().localTranslations(), animator.pose().localRotations(), animator.pose().localScales());
  }

  @Test
  void twoBoneIk_exactLengthAndZeroDistance_doNotProduceNaN() {
    final Skeleton skeleton = chainSkeleton(3);
    final PoseBuffer pose = bindPoseBuffer(skeleton);
    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);
    final TwoBoneIkSolver solver = new TwoBoneIkSolver();

    solver.solve(pose, skeleton, chain, IkTarget.withoutPole(2f, 0f, 0f));
    assertFinitePose(pose.localTranslations(), pose.localRotations(), pose.localScales());
    assertUnitQuaternions(pose.localRotations());

    solver.solve(pose, skeleton, chain, IkTarget.withoutPole(0f, 0f, 0f));
    assertFinitePose(pose.localTranslations(), pose.localRotations(), pose.localScales());
    assertUnitQuaternions(pose.localRotations());
  }

  @Test
  void fabrik_unreachableTarget_terminatesAndProducesFinitePose() {
    final Skeleton skeleton = chainSkeleton(4);
    final PoseBuffer pose = bindPoseBuffer(skeleton);
    final FabrikChainDef chain = new FabrikChainDef("spine", List.of(0, 1, 2, 3), 1e-4f, 5, List.of());

    new FabrikSolver().solve(pose, skeleton, chain, IkTarget.withoutPole(100f, 0f, 0f));
    assertFinitePose(pose.localTranslations(), pose.localRotations(), pose.localScales());
    assertUnitQuaternions(pose.localRotations());
  }

  @Test
  void poseWarper_zeroDelta_isNoOp() {
    final Skeleton skeleton = chainSkeleton(3);
    final PoseBuffer pose = bindPoseBuffer(skeleton);
    final float[] beforeT = pose.localTranslations().clone();
    final float[] beforeR = pose.localRotations().clone();

    new DefaultPoseWarper().warp(
        pose,
        skeleton,
        List.of(new WarpTarget("noop", 2, worldPositionOf(skeleton, pose, 2), 0.5f, 0.5f)));

    assertArrayEquals(beforeT, pose.localTranslations(), 1e-6f);
    assertArrayEquals(beforeR, pose.localRotations(), 1e-6f);
  }

  @Test
  void secondaryMotionSolver_zeroDt_noNaN() {
    final Skeleton skeleton = new Skeleton(
        "secondary",
        chainSkeleton(4).joints(),
        0,
        List.of(new SecondaryChainDef("tail", List.of(1, 2, 3), 0.8f, 0.7f, 0.5f, List.of())));
    final PoseBuffer pose = bindPoseBuffer(skeleton);

    new DefaultSecondaryMotionSolver().solve(pose, skeleton, 0f);
    assertFinitePose(pose.localTranslations(), pose.localRotations(), pose.localScales());
    assertUnitQuaternions(pose.localRotations());
  }

  @Test
  void physicsCharacterController_zeroDt_noNaN() {
    final Skeleton skeleton = chainSkeleton(4);
    final PoseBuffer pose = bindPoseBuffer(skeleton);
    final DefaultPhysicsCharacterController controller = new DefaultPhysicsCharacterController(
        new PhysicsCharacterDef(0.7f, 0.5f, 20f, List.of(0, 1, 2), List.of(3)));

    controller.update(pose, skeleton, 0f);
    final PoseBuffer simulated = controller.simulatedPose();
    assertFinitePose(simulated.localTranslations(), simulated.localRotations(), simulated.localScales());
    assertUnitQuaternions(simulated.localRotations());
  }

  @Test
  void motionMatcher_emptyDatabase_throwsClearError() {
    final MotionDatabase db = new MotionDatabase(List.of(), List.of(), new MotionFeatureSchema(List.of(), 0, 0.1f));
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultMotionMatcher().findBest(db, new MotionQuery(new float[0], new float[0], new float[0])));
    assertTrue(ex.getMessage().contains("no frames"));
  }

  @Test
  void motionMatcher_featureLengthMismatch_throwsClearError() {
    final MotionFrame frame = new MotionFrame(0, 0f, new float[] {0f, 1f}, new float[] {0f}, new float[] {0f});
    final MotionDatabase db = new MotionDatabase(List.of(), List.of(frame), new MotionFeatureSchema(List.of(0), 1, 0.1f));
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultMotionMatcher().findBest(db, new MotionQuery(new float[] {0f}, new float[] {0f}, new float[] {0f})));
    assertTrue(ex.getMessage().contains("length mismatch"));
  }

  @Test
  void randomizedPipelineInputs_doNotProduceNaNOrInf() {
    final Random random = new Random(0xA11BEEFL);
    final int jointCount = 24;
    final Skeleton skeleton = chainSkeleton(jointCount);
    final ClipId clipId = new ClipId("stress");
    final Clip clip = randomClip(random, clipId, jointCount, 32, 1f / 30f);
    final StateMachineDef machine = new StateMachineDef(
        "stress-sm",
        List.of(new StateDef("idle", new ClipNode(clipId, 1f), List.of(new TransitionDef("idle", new org.animis.state.BoolParam("loop", true), 0.05f, false, 0f)))),
        "idle");
    final IkChain ik = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);

    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(clipId, clip),
        Map.of(clipId, true),
        List.of(ik),
        new DefaultSkinningComputer(),
        new PhysicsCharacterDef(0.7f, 0.5f, 20f, List.of(0, 1, 2, 3, 4), List.of(4)));

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.setBool("loop", true);
    animator.setWarpTarget(new WarpTarget("hand", 2, new float[] {1f, 1f, 0f}, 0.25f, 0.2f));

    for (int i = 0; i < 500; i++) {
      final float dt = 1f / (30f + random.nextInt(91));
      if ((i & 7) == 0) {
        animator.setIkTarget("arm", IkTarget.withoutPole(
            randomRange(random, -1.5f, 1.5f),
            randomRange(random, -1.5f, 1.5f),
            randomRange(random, -1.5f, 1.5f)));
      }
      animator.update(dt);
      assertFinitePose(animator.pose().localTranslations(), animator.pose().localRotations(), animator.pose().localScales());
      assertUnitQuaternions(animator.pose().localRotations());
      assertEquals(jointCount * 16, animator.skinningOutput().jointMatrices().length);
    }
  }

  private static AnimatorInstance animatorFor(
      final Skeleton skeleton,
      final StateDef state,
      final Map<ClipId, Clip> clips,
      final Map<ClipId, Boolean> loops) {
    final StateMachineDef machine = new StateMachineDef("m", List.of(state), state.name());
    return new DefaultAnimationRuntime(clips, loops, List.of(), new DefaultSkinningComputer()).create(machine, skeleton);
  }

  private static Clip constantClip(final String id, final int joint, final float tx) {
    final ClipId clipId = new ClipId(id);
    final TrackMetadata metadata = new TrackMetadata(30f, CurveTypeHint.SAMPLED, 2, 1f, null);
    final TransformTrack track = new TransformTrack(
        joint,
        metadata,
        new float[] {tx, 0f, 0f, tx, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f});
    return new Clip(clipId, id, 1f, List.of(track));
  }

  private static Skeleton oneJointSkeleton(final float x, final float y, final float z) {
    return new Skeleton(
        "one",
        List.of(new Joint(0, "root", -1, new BindTransform(x, y, z, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Skeleton chainSkeleton(final int jointCount) {
    final ArrayList<Joint> joints = new ArrayList<>(jointCount);
    joints.add(new Joint(0, "j0", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    for (int i = 1; i < jointCount; i++) {
      joints.add(new Joint(i, "j" + i, i - 1, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    }
    return new Skeleton("chain", joints, 0);
  }

  private static PoseBuffer bindPoseBuffer(final Skeleton skeleton) {
    final PoseBuffer pose = new PoseBuffer(skeleton.joints().size());
    for (final Joint joint : skeleton.joints()) {
      final BindTransform bind = joint.bind();
      pose.setTranslation(joint.index(), bind.tx(), bind.ty(), bind.tz());
      pose.setRotation(joint.index(), bind.qx(), bind.qy(), bind.qz(), bind.qw());
      pose.setScale(joint.index(), bind.sx(), bind.sy(), bind.sz());
    }
    return pose;
  }

  private static float[] worldPositionOf(final Skeleton skeleton, final PoseBuffer pose, final int jointIndex) {
    final float[] world = new float[skeleton.joints().size() * 3];
    final float[] t = pose.localTranslations();
    final float[] r = pose.localRotations();
    for (final Joint joint : skeleton.joints()) {
      final int i = joint.index();
      final int tb = i * 3;
      if (joint.parentIndex() < 0) {
        world[tb] = t[tb];
        world[tb + 1] = t[tb + 1];
        world[tb + 2] = t[tb + 2];
      } else {
        final int p = joint.parentIndex() * 3;
        world[tb] = world[p] + t[tb];
        world[tb + 1] = world[p + 1] + t[tb + 1];
        world[tb + 2] = world[p + 2] + t[tb + 2];
      }
      final int rb = i * 4;
      final float len = (float) Math.sqrt(r[rb] * r[rb] + r[rb + 1] * r[rb + 1] + r[rb + 2] * r[rb + 2] + r[rb + 3] * r[rb + 3]);
      assertTrue(len > 1e-8f);
    }
    final int b = jointIndex * 3;
    return new float[] {world[b], world[b + 1], world[b + 2]};
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

  private static float randomRange(final Random random, final float min, final float max) {
    return min + random.nextFloat() * (max - min);
  }

  private static void assertFinitePose(final float[] translations, final float[] rotations, final float[] scales) {
    assertFiniteArray(translations);
    assertFiniteArray(rotations);
    assertFiniteArray(scales);
  }

  private static void assertFiniteArray(final float[] values) {
    for (final float v : values) {
      assertTrue(Float.isFinite(v));
      assertTrue(Math.abs(v) < 1_000_000f);
    }
  }

  private static void assertUnitQuaternions(final float[] rotations) {
    for (int i = 0; i < rotations.length; i += 4) {
      final float len = (float) Math.sqrt(
          rotations[i] * rotations[i]
              + rotations[i + 1] * rotations[i + 1]
              + rotations[i + 2] * rotations[i + 2]
              + rotations[i + 3] * rotations[i + 3]);
      assertTrue(Math.abs(1f - len) <= 1e-5f);
    }
  }
}
