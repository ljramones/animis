package dev.ljramones.animis.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.clip.CurveTypeHint;
import dev.ljramones.animis.clip.QuantizationSpec;
import dev.ljramones.animis.clip.TrackMetadata;
import dev.ljramones.animis.clip.TransformTrack;
import dev.ljramones.animis.runtime.blend.DefaultBlendEvaluator;
import dev.ljramones.animis.runtime.blend.EvalContext;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.runtime.sampling.DefaultClipSampler;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import dev.ljramones.animis.state.BoolParam;
import dev.ljramones.animis.state.StateDef;
import dev.ljramones.animis.state.StateMachineDef;
import dev.ljramones.animis.state.TransitionDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DefaultStateMachineEvaluatorTest {
  @Test
  void tick_startsTransitionWhenConditionPasses() {
    final Fixture f = fixture(false);
    f.boolParams.put("go", true);

    final StateMachineInstance sm = new StateMachineInstance(f.machine);
    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(f.blendEvaluator);
    final PoseBuffer out = new PoseBuffer(1);

    evaluator.tick(sm, 0f, f.context(), out);

    assertTrue(sm.hasActiveTransition());
    assertEquals("idle", sm.activeTransition().fromStateName());
    assertEquals("run", sm.activeTransition().toStateName());
  }

  @Test
  void tick_completesCrossFadeAndPromotesIncomingState() {
    final Fixture f = fixture(false);
    f.boolParams.put("go", true);

    final StateMachineInstance sm = new StateMachineInstance(f.machine);
    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(f.blendEvaluator);
    final PoseBuffer out = new PoseBuffer(1);

    evaluator.tick(sm, 0f, f.context(), out);
    evaluator.tick(sm, 0.6f, f.context(), out);

    assertFalse(sm.hasActiveTransition());
    assertEquals("run", sm.currentStateName());
    evaluator.tick(sm, 0.016f, f.context(), out);
    assertEquals(10f, out.localTranslations()[0], 1e-5f);
  }

  @Test
  void tick_respectsExitTimeGuard() {
    final Fixture f = fixture(true);
    f.boolParams.put("go", true);

    final StateMachineInstance sm = new StateMachineInstance(f.machine);
    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(f.blendEvaluator);
    final PoseBuffer out = new PoseBuffer(1);

    f.clipTimes.put(f.idleId, 0.5f);
    evaluator.tick(sm, 0f, f.context(), out);
    assertFalse(sm.hasActiveTransition());

    f.clipTimes.put(f.idleId, 0.8f);
    evaluator.tick(sm, 0f, f.context(), out);
    assertTrue(sm.hasActiveTransition());
  }

  @Test
  void inertialOffsetsConvergeToZero() {
    final Fixture f = fixture(false, 0.15f);
    f.boolParams.put("go", true);

    final StateMachineInstance sm = new StateMachineInstance(f.machine);
    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(f.blendEvaluator);
    final PoseBuffer out = new PoseBuffer(1);

    evaluator.tick(sm, 0f, f.context(), out);
    evaluator.tick(sm, 0.016f, f.context(), out);
    final float earlyError = Math.abs(10f - out.localTranslations()[0]);

    for (int i = 0; i < 60; i++) {
      evaluator.tick(sm, 0.016f, f.context(), out);
    }
    final float lateError = Math.abs(10f - out.localTranslations()[0]);

    assertTrue(lateError < earlyError);
    assertTrue(lateError < 1e-2f);
  }

  @Test
  void smallerHalfLifeConvergesFaster() {
    final Fixture fast = fixture(false, 0.05f);
    final Fixture slow = fixture(false, 0.30f);
    fast.boolParams.put("go", true);
    slow.boolParams.put("go", true);

    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(fast.blendEvaluator);
    final StateMachineInstance fastSm = new StateMachineInstance(fast.machine);
    final StateMachineInstance slowSm = new StateMachineInstance(slow.machine);
    final PoseBuffer fastOut = new PoseBuffer(1);
    final PoseBuffer slowOut = new PoseBuffer(1);

    evaluator.tick(fastSm, 0f, fast.context(), fastOut);
    evaluator.tick(slowSm, 0f, slow.context(), slowOut);
    evaluator.tick(fastSm, 0.05f, fast.context(), fastOut);
    evaluator.tick(slowSm, 0.05f, slow.context(), slowOut);

    final float fastError = Math.abs(10f - fastOut.localTranslations()[0]);
    final float slowError = Math.abs(10f - slowOut.localTranslations()[0]);
    assertTrue(fastError < slowError);
  }

  @Test
  void backToBackTransitionsDoNotAccumulateDrift() {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final Clip idle = clip(idleId, 0f, 0f);
    final Clip run = clip(runId, 10f, 10f);

    final TransitionDef idleToRun = new TransitionDef("run", new BoolParam("go", true), 0.15f, false, 0f, 0.1f);
    final TransitionDef runToIdle = new TransitionDef("idle", new BoolParam("back", true), 0.15f, false, 0f, 0.1f);
    final StateDef idleState = new StateDef("idle", new ClipNode(idleId, 1f), List.of(idleToRun));
    final StateDef runState = new StateDef("run", new ClipNode(runId, 1f), List.of(runToIdle));
    final StateMachineDef machine = new StateMachineDef("m", List.of(idleState, runState), "idle");

    final Map<ClipId, Clip> clips = Map.of(idleId, idle, runId, run);
    final Map<ClipId, Float> clipTimes = new HashMap<>();
    clipTimes.put(idleId, 0f);
    clipTimes.put(runId, 0f);
    final Map<ClipId, Boolean> clipLoops = Map.of(idleId, false, runId, false);
    final Map<String, Boolean> boolParams = new HashMap<>();
    final EvalContext ctx = new EvalContext(skeleton, clips, clipTimes, clipLoops, boolParams, new HashMap<>(), null, null);

    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    final StateMachineInstance sm = new StateMachineInstance(machine);
    final PoseBuffer out = new PoseBuffer(1);

    boolParams.put("go", true);
    evaluator.tick(sm, 0f, ctx, out);
    for (int i = 0; i < 20; i++) {
      evaluator.tick(sm, 0.016f, ctx, out);
    }

    boolParams.put("go", false);
    boolParams.put("back", true);
    evaluator.tick(sm, 0f, ctx, out);
    for (int i = 0; i < 40; i++) {
      evaluator.tick(sm, 0.016f, ctx, out);
    }
    assertEquals("idle", sm.currentStateName());
    assertTrue(Math.abs(out.localTranslations()[0]) < 1e-2f);
  }

  @Test
  void interrupt_midTransitionProducesValidPose() {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");
    final ClipId jumpId = new ClipId("jump");
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final StateDef idle = new StateDef(
        "idle",
        new ClipNode(idleId, 1f),
        List.of(new TransitionDef("run", new BoolParam("go", true), 0.5f, false, 0f, 0.15f)));
    final StateDef run = new StateDef(
        "run",
        new ClipNode(runId, 1f),
        List.of(new TransitionDef("jump", new BoolParam("jump", true), 0.3f, false, 0f, 0.15f)));
    final StateDef jump = new StateDef("jump", new ClipNode(jumpId, 1f), List.of());
    final StateMachineDef machine = new StateMachineDef("m", List.of(idle, run, jump), "idle");

    final Map<ClipId, Clip> clips = Map.of(idleId, clip(idleId, 0f, 0f), runId, clip(runId, 10f, 10f), jumpId, clip(jumpId, 20f, 20f));
    final Map<ClipId, Float> clipTimes = new HashMap<>();
    clipTimes.put(idleId, 0f);
    clipTimes.put(runId, 0f);
    clipTimes.put(jumpId, 0f);
    final Map<ClipId, Boolean> loops = Map.of(idleId, false, runId, false, jumpId, false);
    final Map<String, Boolean> bools = new HashMap<>();
    final EvalContext ctx = new EvalContext(skeleton, clips, clipTimes, loops, bools, new HashMap<>(), null, null);

    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    final StateMachineInstance sm = new StateMachineInstance(machine);
    final PoseBuffer out = new PoseBuffer(1);

    bools.put("go", true);
    evaluator.tick(sm, 0f, ctx, out);
    evaluator.tick(sm, 0.1f, ctx, out);
    bools.put("jump", true);
    evaluator.tick(sm, 0.016f, ctx, out);

    assertTrue(sm.hasActiveTransition());
    assertEquals("run", sm.activeTransition().fromStateName());
    assertEquals("jump", sm.activeTransition().toStateName());
    assertNotNull(sm.activeTransition().interruptSnapshot());
    assertTrue(out.localTranslations()[0] >= 0f && out.localTranslations()[0] <= 20f);
  }

  @Test
  void chainedInterrupts_doNotAccumulateDrift() {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");
    final ClipId jumpId = new ClipId("jump");
    final ClipId landId = new ClipId("land");
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);

    final StateDef idle = new StateDef("idle", new ClipNode(idleId, 1f), List.of(new TransitionDef("run", new BoolParam("go", true), 0.4f, false, 0f, 0.1f)));
    final StateDef run = new StateDef("run", new ClipNode(runId, 1f), List.of(new TransitionDef("jump", new BoolParam("jump", true), 0.4f, false, 0f, 0.1f)));
    final StateDef jump = new StateDef("jump", new ClipNode(jumpId, 1f), List.of(new TransitionDef("land", new BoolParam("land", true), 0.4f, false, 0f, 0.1f)));
    final StateDef land = new StateDef("land", new ClipNode(landId, 1f), List.of());
    final StateMachineDef machine = new StateMachineDef("m", List.of(idle, run, jump, land), "idle");

    final Map<ClipId, Clip> clips = Map.of(
        idleId, clip(idleId, 0f, 0f),
        runId, clip(runId, 10f, 10f),
        jumpId, clip(jumpId, 20f, 20f),
        landId, clip(landId, 5f, 5f));
    final Map<ClipId, Float> clipTimes = new HashMap<>();
    clipTimes.put(idleId, 0f);
    clipTimes.put(runId, 0f);
    clipTimes.put(jumpId, 0f);
    clipTimes.put(landId, 0f);
    final Map<ClipId, Boolean> loops = Map.of(idleId, false, runId, false, jumpId, false, landId, false);
    final Map<String, Boolean> bools = new HashMap<>();
    final EvalContext ctx = new EvalContext(skeleton, clips, clipTimes, loops, bools, new HashMap<>(), null, null);

    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    final StateMachineInstance sm = new StateMachineInstance(machine);
    final PoseBuffer out = new PoseBuffer(1);

    bools.put("go", true);
    evaluator.tick(sm, 0f, ctx, out);
    evaluator.tick(sm, 0.08f, ctx, out);
    bools.put("jump", true);
    evaluator.tick(sm, 0.016f, ctx, out);
    bools.put("land", true);
    evaluator.tick(sm, 0.016f, ctx, out);
    for (int i = 0; i < 120; i++) {
      evaluator.tick(sm, 0.016f, ctx, out);
    }
    assertEquals("land", sm.currentStateName());
    assertTrue(Math.abs(out.localTranslations()[0] - 5f) < 0.05f);
  }

  @Test
  void interruptedInertialBlend_convergesToLatestTarget() {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");
    final ClipId jumpId = new ClipId("jump");
    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);

    final StateDef idle = new StateDef("idle", new ClipNode(idleId, 1f), List.of(new TransitionDef("run", new BoolParam("go", true), 1.0f, false, 0f, 0.2f)));
    final StateDef run = new StateDef("run", new ClipNode(runId, 1f), List.of(new TransitionDef("jump", new BoolParam("jump", true), 1.0f, false, 0f, 0.2f)));
    final StateDef jump = new StateDef("jump", new ClipNode(jumpId, 1f), List.of());
    final StateMachineDef machine = new StateMachineDef("m", List.of(idle, run, jump), "idle");

    final Map<ClipId, Clip> clips = Map.of(idleId, clip(idleId, 0f, 0f), runId, clip(runId, 10f, 10f), jumpId, clip(jumpId, 20f, 20f));
    final Map<ClipId, Float> clipTimes = new HashMap<>();
    clipTimes.put(idleId, 0f);
    clipTimes.put(runId, 0f);
    clipTimes.put(jumpId, 0f);
    final Map<ClipId, Boolean> loops = Map.of(idleId, false, runId, false, jumpId, false);
    final Map<String, Boolean> bools = new HashMap<>();
    final EvalContext ctx = new EvalContext(skeleton, clips, clipTimes, loops, bools, new HashMap<>(), null, null);

    final DefaultStateMachineEvaluator evaluator = new DefaultStateMachineEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    final StateMachineInstance sm = new StateMachineInstance(machine);
    final PoseBuffer out = new PoseBuffer(1);

    bools.put("go", true);
    evaluator.tick(sm, 0f, ctx, out);
    evaluator.tick(sm, 0.12f, ctx, out);
    bools.put("jump", true);
    evaluator.tick(sm, 0.016f, ctx, out);
    final float earlyError = Math.abs(20f - out.localTranslations()[0]);
    for (int i = 0; i < 180; i++) {
      evaluator.tick(sm, 0.016f, ctx, out);
    }
    final float lateError = Math.abs(20f - out.localTranslations()[0]);
    assertTrue(lateError < earlyError);
    assertTrue(lateError < 0.05f);
  }

  private static Fixture fixture(final boolean hasExitTime) {
    return fixture(hasExitTime, 0.15f);
  }

  private static Fixture fixture(final boolean hasExitTime, final float halfLife) {
    final ClipId idleId = new ClipId("idle");
    final ClipId runId = new ClipId("run");

    final Skeleton skeleton = new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);

    final Clip idle = clip(idleId, 0f, 0f);
    final Clip run = clip(runId, 10f, 10f);

    final TransitionDef idleToRun = new TransitionDef(
        "run",
        new BoolParam("go", true),
        0.5f,
        hasExitTime,
        0.7f,
        halfLife);

    final StateDef idleState = new StateDef("idle", new ClipNode(idleId, 1f), List.of(idleToRun));
    final StateDef runState = new StateDef("run", new ClipNode(runId, 1f), List.of());
    final StateMachineDef machine = new StateMachineDef("locomotion", List.of(idleState, runState), "idle");

    final Map<ClipId, Clip> clips = Map.of(idleId, idle, runId, run);
    final Map<ClipId, Float> clipTimes = new HashMap<>();
    clipTimes.put(idleId, 0f);
    clipTimes.put(runId, 0f);
    final Map<ClipId, Boolean> clipLoops = Map.of(idleId, false, runId, false);
    final Map<String, Boolean> boolParams = new HashMap<>();
    final Map<String, Float> floatParams = new HashMap<>();

    final DefaultBlendEvaluator blendEvaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    return new Fixture(
        machine,
        skeleton,
        clips,
        clipTimes,
        clipLoops,
        boolParams,
        floatParams,
        blendEvaluator,
        idleId);
  }

  private static Clip clip(final ClipId id, final float x0, final float x1) {
    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        2,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {x0, 0f, 0f, x1, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f});
    return new Clip(id, id.value(), 1f, List.of(track));
  }

  private record Fixture(
      StateMachineDef machine,
      Skeleton skeleton,
      Map<ClipId, Clip> clips,
      Map<ClipId, Float> clipTimes,
      Map<ClipId, Boolean> clipLoops,
      Map<String, Boolean> boolParams,
      Map<String, Float> floatParams,
      DefaultBlendEvaluator blendEvaluator,
      ClipId idleId) {
    private EvalContext context() {
      return new EvalContext(
          this.skeleton,
          this.clips,
          this.clipTimes,
          this.clipLoops,
          this.boolParams,
          this.floatParams,
          null,
          null);
    }
  }
}
