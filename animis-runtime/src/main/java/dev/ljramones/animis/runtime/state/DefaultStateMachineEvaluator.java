package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.blend.AddNode;
import dev.ljramones.animis.blend.BlendNode;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.LerpNode;
import dev.ljramones.animis.blend.OneDChild;
import dev.ljramones.animis.blend.OneDNode;
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
  private final ThreadLocal<PoseBuffer> scratch = new ThreadLocal<>();

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
      tryStartTransition(sm, ctx);
    }

    if (!sm.hasActiveTransition()) {
      this.blendEvaluator.evaluate(sm.currentState().motion(), ctx, outPose);
      return;
    }

    sm.advanceTransition(dt);
    final StateMachineInstance.ActiveTransition transition = sm.activeTransition();
    final StateDef fromState = sm.state(transition.fromStateName());
    final StateDef toState = sm.state(transition.toStateName());

    final float alpha =
        transition.blendSeconds() <= 0f
            ? 1f
            : clamp01(transition.elapsedSeconds() / transition.blendSeconds());

    final PoseBuffer fromPose = scratch(outPose.jointCount());
    this.blendEvaluator.evaluate(fromState.motion(), ctx, fromPose);
    this.blendEvaluator.evaluate(toState.motion(), ctx, outPose);
    blend(fromPose, outPose, alpha, outPose);

    if (alpha >= 1f) {
      sm.completeTransition();
    }
  }

  private void tryStartTransition(final StateMachineInstance sm, final EvalContext ctx) {
    final StateDef current = sm.currentState();
    for (final TransitionDef transition : current.transitions()) {
      if (!evaluateCondition(transition.condition(), ctx)) {
        continue;
      }
      if (transition.hasExitTime()
          && normalizedStateTime(current.motion(), ctx) < transition.exitTimeNormalized()) {
        continue;
      }
      sm.startTransition(transition.toState(), transition.blendSeconds());
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
    throw new IllegalArgumentException("Unsupported BlendNode: " + node.getClass().getName());
  }

  private PoseBuffer scratch(final int jointCount) {
    PoseBuffer buffer = this.scratch.get();
    if (buffer == null || buffer.jointCount() != jointCount) {
      buffer = new PoseBuffer(jointCount);
      this.scratch.set(buffer);
    }
    return buffer;
  }

  private static void blend(
      final PoseBuffer fromPose,
      final PoseBuffer toPose,
      final float alpha,
      final PoseBuffer outPose) {
    final float[] ft = fromPose.localTranslations();
    final float[] tt = toPose.localTranslations();
    final float[] fs = fromPose.localScales();
    final float[] ts = toPose.localScales();
    final float[] fr = fromPose.localRotations();
    final float[] tr = toPose.localRotations();

    for (int i = 0; i < outPose.jointCount(); i++) {
      final int tBase = i * 3;
      outPose.setTranslation(
          i,
          lerp(ft[tBase], tt[tBase], alpha),
          lerp(ft[tBase + 1], tt[tBase + 1], alpha),
          lerp(ft[tBase + 2], tt[tBase + 2], alpha));
      outPose.setScale(
          i,
          lerp(fs[tBase], ts[tBase], alpha),
          lerp(fs[tBase + 1], ts[tBase + 1], alpha),
          lerp(fs[tBase + 2], ts[tBase + 2], alpha));

      final int rBase = i * 4;
      final float[] q = slerp(
          fr[rBase], fr[rBase + 1], fr[rBase + 2], fr[rBase + 3],
          tr[rBase], tr[rBase + 1], tr[rBase + 2], tr[rBase + 3],
          alpha);
      outPose.setRotation(i, q[0], q[1], q[2], q[3]);
    }
  }

  private static float lerp(final float a, final float b, final float t) {
    return a + (b - a) * t;
  }

  private static float clamp01(final float x) {
    return Math.max(0f, Math.min(1f, x));
  }

  private static float[] slerp(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bxIn,
      final float byIn,
      final float bzIn,
      final float bwIn,
      final float t) {
    float bx = bxIn;
    float by = byIn;
    float bz = bzIn;
    float bw = bwIn;
    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0f) {
      dot = -dot;
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
    }
    if (dot > 0.9995f) {
      return normalize(lerp(ax, bx, t), lerp(ay, by, t), lerp(az, bz, t), lerp(aw, bw, t));
    }
    final float theta0 = (float) Math.acos(dot);
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);
    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalize(s0 * ax + s1 * bx, s0 * ay + s1 * by, s0 * az + s1 * bz, s0 * aw + s1 * bw);
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 0f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float invLen = 1f / (float) Math.sqrt(lenSq);
    return new float[] {x * invLen, y * invLen, z * invLen, w * invLen};
  }
}
