package dev.ljramones.animis.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  private static Fixture fixture(final boolean hasExitTime) {
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
        0.7f);

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
          this.floatParams);
    }
  }
}
