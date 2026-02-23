package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.blend.AddNode;
import dev.ljramones.animis.blend.BlendNode;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.LerpNode;
import dev.ljramones.animis.blend.OneDChild;
import dev.ljramones.animis.blend.OneDNode;
import dev.ljramones.animis.blend.ProceduralNode;
import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.runtime.blend.BlendEvaluator;
import dev.ljramones.animis.runtime.blend.EvalContext;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.state.AndExpr;
import dev.ljramones.animis.state.BoolParam;
import dev.ljramones.animis.state.CompareOp;
import dev.ljramones.animis.state.ConditionExpr;
import dev.ljramones.animis.state.FloatCompare;
import dev.ljramones.animis.state.NotExpr;
import dev.ljramones.animis.state.OrExpr;
import dev.ljramones.animis.state.StateDef;
import dev.ljramones.animis.state.TransitionDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class DefaultStateMachineEvaluator implements StateMachineEvaluator {
  private final BlendEvaluator blendEvaluator;
  private final InertialBlendEvaluator inertialBlendEvaluator = new InertialBlendEvaluator();
  private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

  public DefaultStateMachineEvaluator(final BlendEvaluator blendEvaluator) {
    this.blendEvaluator = blendEvaluator;
  }

  @Override
  public void tick(
      final StateMachineInstance sm,
      final float dt,
      final EvalContext ctx,
      final PoseBuffer outPose) {
    if (!sm.hasActiveTransition()) {
      tryStartTransition(sm, ctx, outPose);
    }

    if (!sm.hasActiveTransition()) {
      this.blendEvaluator.evaluate(sm.currentState().motion(), ctx, outPose);
      sm.captureLastPose(outPose.localTranslations(), outPose.localRotations(), outPose.localScales(), dt);
      return;
    }

    sm.advanceTransition(dt);
    final Scratch s = this.scratch.get();
    final StateMachineInstance.ActiveTransition transition = sm.activeTransition();
    final StateDef toState = sm.state(transition.toStateName());
    final PoseBuffer targetPose = s.target(outPose.jointCount());
    this.blendEvaluator.evaluate(toState.motion(), ctx, targetPose);
    this.inertialBlendEvaluator.apply(
        transition.inertialState(),
        transition.halfLife(),
        dt,
        targetPose,
        outPose);
    sm.captureLastPose(outPose.localTranslations(), outPose.localRotations(), outPose.localScales(), dt);

    if (transition.elapsedSeconds() >= transition.blendSeconds()) {
      sm.completeTransition();
    }
  }

  private void tryStartTransition(final StateMachineInstance sm, final EvalContext ctx, final PoseBuffer outPose) {
    final StateDef current = sm.currentState();
    for (final TransitionDef transition : current.transitions()) {
      if (!evaluateCondition(transition.condition(), ctx)) {
        continue;
      }
      if (transition.hasExitTime()
          && normalizedStateTime(current.motion(), ctx) < transition.exitTimeNormalized()) {
        continue;
      }
      final Scratch s = this.scratch.get();
      final PoseBuffer fromPose = s.from(outPose.jointCount());
      final PoseBuffer toPose = s.target(outPose.jointCount());
      this.blendEvaluator.evaluate(current.motion(), ctx, fromPose);
      this.blendEvaluator.evaluate(sm.state(transition.toState()).motion(), ctx, toPose);
      final InertialState inertialState =
          InertialState.capture(
              fromPose,
              toPose,
              sm.hasLastPose() ? sm.lastTranslations() : null,
              sm.hasLastPose() ? sm.lastRotations() : null,
              sm.hasLastPose() ? sm.lastScales() : null,
              sm.lastDtSeconds());
      sm.startTransition(
          transition.toState(),
          transition.blendSeconds(),
          transition.halfLife(),
          inertialState);
      return;
    }
  }

  private boolean evaluateCondition(final ConditionExpr condition, final EvalContext ctx) {
    if (condition instanceof BoolParam b) {
      return ctx.boolParams().getOrDefault(b.name(), false) == b.expected();
    }
    if (condition instanceof FloatCompare f) {
      final float value = ctx.floatParams().getOrDefault(f.name(), 0f);
      return compare(value, f.op(), f.value());
    }
    if (condition instanceof AndExpr and) {
      for (final ConditionExpr term : and.terms()) {
        if (!evaluateCondition(term, ctx)) {
          return false;
        }
      }
      return true;
    }
    if (condition instanceof OrExpr or) {
      for (final ConditionExpr term : or.terms()) {
        if (evaluateCondition(term, ctx)) {
          return true;
        }
      }
      return false;
    }
    if (condition instanceof NotExpr not) {
      return !evaluateCondition(not.term(), ctx);
    }
    throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass().getName());
  }

  private static boolean compare(final float a, final CompareOp op, final float b) {
    return switch (op) {
      case LT -> a < b;
      case LTE -> a <= b;
      case GT -> a > b;
      case GTE -> a >= b;
      case EQ -> a == b;
      case NEQ -> a != b;
    };
  }

  private static float normalizedStateTime(final BlendNode motion, final EvalContext ctx) {
    final Set<ClipId> clipIds = new HashSet<>();
    collectClipIds(motion, clipIds);
    if (clipIds.isEmpty()) {
      return 1f;
    }
    float max = 0f;
    for (final ClipId clipId : clipIds) {
      final Clip clip = ctx.clips().get(clipId);
      if (clip == null) {
        continue;
      }
      final float duration = clip.durationSeconds();
      if (duration <= 0f) {
        max = Math.max(max, 1f);
        continue;
      }
      float time = ctx.clipTimes().getOrDefault(clipId, 0f);
      final boolean loop = ctx.clipLoops().getOrDefault(clipId, true);
      if (loop) {
        final float wrapped = time % duration;
        time = wrapped < 0f ? wrapped + duration : wrapped;
      } else {
        time = Math.max(0f, Math.min(duration, time));
      }
      max = Math.max(max, Math.max(0f, Math.min(1f, time / duration)));
    }
    return max;
  }

  private static void collectClipIds(final BlendNode node, final Set<ClipId> out) {
    if (node instanceof ClipNode clipNode) {
      out.add(clipNode.clipId());
      return;
    }
    if (node instanceof LerpNode lerpNode) {
      collectClipIds(lerpNode.a(), out);
      collectClipIds(lerpNode.b(), out);
      return;
    }
    if (node instanceof AddNode addNode) {
      collectClipIds(addNode.base(), out);
      collectClipIds(addNode.additive(), out);
      return;
    }
    if (node instanceof OneDNode oneDNode) {
      for (final OneDChild child : new ArrayList<>(oneDNode.children())) {
        collectClipIds(child.node(), out);
      }
      return;
    }
    if (node instanceof ProceduralNode) {
      return;
    }
    throw new IllegalArgumentException("Unsupported BlendNode: " + node.getClass().getName());
  }

  private static final class Scratch {
    private PoseBuffer fromPose;
    private PoseBuffer targetPose;

    private PoseBuffer from(final int jointCount) {
      if (this.fromPose == null || this.fromPose.jointCount() != jointCount) {
        this.fromPose = new PoseBuffer(jointCount);
      }
      return this.fromPose;
    }

    private PoseBuffer target(final int jointCount) {
      if (this.targetPose == null || this.targetPose.jointCount() != jointCount) {
        this.targetPose = new PoseBuffer(jointCount);
      }
      return this.targetPose;
    }
  }
}
