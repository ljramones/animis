package org.animis.runtime.state;

import org.animis.blend.AddNode;
import org.animis.blend.BlendNode;
import org.animis.blend.ClipNode;
import org.animis.blend.LerpNode;
import org.animis.blend.OneDChild;
import org.animis.blend.OneDNode;
import org.animis.blend.ProceduralNode;
import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.runtime.blend.BlendEvaluator;
import org.animis.runtime.blend.EvalContext;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.state.AndExpr;
import org.animis.state.BoolParam;
import org.animis.state.CompareOp;
import org.animis.state.ConditionExpr;
import org.animis.state.FloatCompare;
import org.animis.state.NotExpr;
import org.animis.state.OrExpr;
import org.animis.state.StateDef;
import org.animis.state.TransitionDef;
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
    tryInterruptTransition(sm, ctx, outPose);
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

  private void tryInterruptTransition(
      final StateMachineInstance sm,
      final EvalContext ctx,
      final PoseBuffer outPose) {
    if (!sm.hasActiveTransition()) {
      return;
    }
    final StateMachineInstance.ActiveTransition active = sm.activeTransition();
    final StateDef currentTargetState = sm.state(active.toStateName());
    for (final TransitionDef transition : currentTargetState.transitions()) {
      if (!evaluateCondition(transition.condition(), ctx)) {
        continue;
      }
      if (transition.hasExitTime()
          && normalizedStateTime(currentTargetState.motion(), ctx) < transition.exitTimeNormalized()) {
        continue;
      }
      final Scratch s = this.scratch.get();
      final PoseBuffer currentSnapshot = s.snapshot(outPose.jointCount());
      copyPose(outPose, currentSnapshot);
      final PoseBuffer toPose = s.target(outPose.jointCount());
      this.blendEvaluator.evaluate(sm.state(transition.toState()).motion(), ctx.withoutRootMotion(), toPose);
      final InertialState inertialState =
          InertialState.capture(
              currentSnapshot,
              toPose,
              sm.hasLastPose() ? sm.lastTranslations() : null,
              sm.hasLastPose() ? sm.lastRotations() : null,
              sm.hasLastPose() ? sm.lastScales() : null,
              sm.lastDtSeconds());
      sm.startTransition(
          active.toStateName(),
          transition.toState(),
          transition.blendSeconds(),
          transition.halfLife(),
          currentSnapshot,
          inertialState);
      return;
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
      this.blendEvaluator.evaluate(current.motion(), ctx.withoutRootMotion(), fromPose);
      this.blendEvaluator.evaluate(sm.state(transition.toState()).motion(), ctx.withoutRootMotion(), toPose);
      final InertialState inertialState =
          InertialState.capture(
              fromPose,
              toPose,
              sm.hasLastPose() ? sm.lastTranslations() : null,
              sm.hasLastPose() ? sm.lastRotations() : null,
              sm.hasLastPose() ? sm.lastScales() : null,
              sm.lastDtSeconds());
      sm.startTransition(
          current.name(),
          transition.toState(),
          transition.blendSeconds(),
          transition.halfLife(),
          null,
          inertialState);
      return;
    }
  }

  private static void copyPose(final PoseBuffer src, final PoseBuffer dst) {
    final float[] st = src.localTranslations();
    final float[] sr = src.localRotations();
    final float[] ss = src.localScales();
    for (int i = 0; i < dst.jointCount(); i++) {
      final int tb = i * 3;
      final int rb = i * 4;
      dst.setTranslation(i, st[tb], st[tb + 1], st[tb + 2]);
      dst.setRotation(i, sr[rb], sr[rb + 1], sr[rb + 2], sr[rb + 3]);
      dst.setScale(i, ss[tb], ss[tb + 1], ss[tb + 2]);
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
    private PoseBuffer snapshotPose;

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

    private PoseBuffer snapshot(final int jointCount) {
      if (this.snapshotPose == null || this.snapshotPose.jointCount() != jointCount) {
        this.snapshotPose = new PoseBuffer(jointCount);
      }
      return this.snapshotPose;
    }
  }
}
