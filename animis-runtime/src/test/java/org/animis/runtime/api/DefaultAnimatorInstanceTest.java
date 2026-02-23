package org.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.blend.ClipNode;
import org.animis.blend.LerpNode;
import org.animis.clip.AnimationEvent;
import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.ik.IkChain;
import org.animis.runtime.ik.IkTarget;
import org.animis.runtime.skinning.DefaultSkinningComputer;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.animis.state.BoolParam;
import org.animis.state.StateDef;
import org.animis.state.StateMachineDef;
import org.animis.state.TransitionDef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DefaultAnimatorInstanceTest {
  @Test
  void update_producesPoseAndSkinningOutput() {
    final ClipId idleId = new ClipId("idle");
    final Clip clip = clip(idleId, 0f);
    final Skeleton skeleton = oneJointSkeleton();
    final StateMachineDef machine = new StateMachineDef(
        "m",
        List.of(new StateDef("idle", new ClipNode(idleId, 1f), List.of())),
        "idle");

    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(idleId, clip),
        Map.of(idleId, true),
        List.of(),
        new DefaultSkinningComputer());

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.update(0.016f);

    assertNotNull(animator.pose());
    assertNotNull(animator.skinningOutput());
    assertTrue(animator.skinningOutput().jointMatrices().length == 16);
  }

  @Test
  void parameterChange_triggersStateTransition() {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");
    final Skeleton skeleton = oneJointSkeleton();
    final StateMachineDef machine = new StateMachineDef(
        "m",
        List.of(
            new StateDef(
                "idle",
                new ClipNode(idleId, 1f),
                List.of(new TransitionDef("run", new BoolParam("go", true), 0f, false, 0f))),
            new StateDef("run", new ClipNode(runId, 1f), List.of())),
        "idle");

    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(idleId, clip(idleId, 0f), runId, clip(runId, 10f)),
        Map.of(idleId, true, runId, true),
        List.of(),
        null);

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.update(0f);
    final float before = animator.pose().localTranslations()[0];

    animator.setBool("go", true);
    animator.update(0.05f);
    animator.update(0.05f);
    final float after = animator.pose().localTranslations()[0];

    assertTrue(before < after);
    assertNull(animator.skinningOutput());
  }

  @Test
  void ikTarget_modifiesJointOutput() {
    final ClipId clipId = new ClipId("idle");
    final Skeleton skeleton = threeJointSkeleton();
    final StateMachineDef machine = new StateMachineDef(
        "m",
        List.of(new StateDef("idle", new ClipNode(clipId, 1f), List.of())),
        "idle");

    final IkChain chain = new IkChain("arm", 0, 1, 2, Optional.empty(), 0f, 1f);
    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(clipId, new Clip(clipId, "idle", 1f, List.of())),
        Map.of(clipId, true),
        List.of(chain),
        null);

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.update(0f);
    final float[] before = animator.pose().localRotations();

    animator.setIkTarget("arm", IkTarget.withPole(1f, 1f, 0f, 0f, 0f, 1f));
    animator.update(0f);
    final float[] after = animator.pose().localRotations();

    float delta = 0f;
    for (int i = 0; i < 8; i++) {
      delta += Math.abs(after[i] - before[i]);
    }
    assertTrue(delta > 1e-5f);
  }

  @Test
  void blendedClips_fireEventAtWeightedNormalizedTime() {
    final ClipId a = new ClipId("a");
    final ClipId b = new ClipId("b");
    final Skeleton skeleton = oneJointSkeleton();
    final StateMachineDef machine = new StateMachineDef(
        "m",
        List.of(
            new StateDef(
                "idle",
                new LerpNode(new ClipNode(a, 1f), new ClipNode(b, 1f), "blend"),
                List.of())),
        "idle");

    final Clip clipA = clipWithEvent(a, 0f, "step", 0.2f);
    final Clip clipB = clipWithEvent(b, 0f, "step", 0.6f);
    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        Map.of(a, clipA, b, clipB),
        Map.of(a, true, b, true),
        List.of(),
        null);

    final AnimatorInstance animator = runtime.create(machine, skeleton);
    animator.setFloat("blend", 0.5f);
    final AtomicInteger fired = new AtomicInteger();
    animator.setEventListener("step", fired::incrementAndGet);

    animator.update(0.39f);
    assertTrue(fired.get() == 0);
    animator.update(0.02f);
    assertTrue(fired.get() == 1);
    animator.update(0.10f);
    assertTrue(fired.get() == 1);
  }

  private static Clip clip(final ClipId id, final float x) {
    final TrackMetadata metadata = new TrackMetadata(1f, null, 1, 1f, null);
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {x, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    return new Clip(id, id.value(), 1f, List.of(track));
  }

  private static Clip clipWithEvent(final ClipId id, final float x, final String eventName, final float normalizedTime) {
    final TrackMetadata metadata = new TrackMetadata(1f, null, 1, 1f, null);
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {x, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    return new Clip(
        id,
        id.value(),
        1f,
        List.of(track),
        Optional.empty(),
        List.of(new AnimationEvent(eventName, normalizedTime)));
  }

  private static Skeleton oneJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Skeleton threeJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(
            new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "mid", 0, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(2, "tip", 1, new BindTransform(1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }
}
