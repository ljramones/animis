package dev.ljramones.animis.runtime.blend;

import dev.ljramones.animis.blend.AddNode;
import dev.ljramones.animis.blend.BlendNode;
import dev.ljramones.animis.blend.BreathingNode;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.HeadTurnNode;
import dev.ljramones.animis.blend.LerpNode;
import dev.ljramones.animis.blend.OneDChild;
import dev.ljramones.animis.blend.OneDNode;
import dev.ljramones.animis.blend.ProceduralNode;
import dev.ljramones.animis.blend.WeightShiftNode;
import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.runtime.api.RootMotionDelta;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.runtime.sampling.ClipSampler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultBlendEvaluator implements BlendEvaluator {
  private static final int INITIAL_SCRATCH_CAPACITY = 8;
  private final ClipSampler clipSampler;
  private final ThreadLocal<java.util.Map<Integer, float[]>> headTrackingState =
      ThreadLocal.withInitial(java.util.HashMap::new);
  private final ThreadLocal<ScratchPool> scratchPool =
      ThreadLocal.withInitial(() -> new ScratchPool(INITIAL_SCRATCH_CAPACITY));

  public DefaultBlendEvaluator(final ClipSampler clipSampler) {
    this.clipSampler = clipSampler;
  }

  @Override
  public void evaluate(final BlendNode node, final EvalContext ctx, final PoseBuffer outPose) {
    if (ctx.skeleton() == null) {
      throw new IllegalArgumentException("EvalContext.skeleton is required");
    }
    final ScratchPool scratch = this.scratchPool.get();
    scratch.begin(outPose.jointCount());
    evaluateNode(node, ctx, outPose, scratch);
  }

  private void evaluateNode(
      final BlendNode node,
      final EvalContext ctx,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    if (node instanceof ClipNode clipNode) {
      evalClipNode(clipNode, ctx, outPose);
      return;
    }
    if (node instanceof LerpNode lerpNode) {
      evalLerpNode(lerpNode, ctx, outPose, scratch);
      return;
    }
    if (node instanceof OneDNode oneDNode) {
      evalOneDNode(oneDNode, ctx, outPose, scratch);
      return;
    }
    if (node instanceof AddNode addNode) {
      evalAddNode(addNode, ctx, outPose, scratch);
      return;
    }
    if (node instanceof ProceduralNode proceduralNode) {
      evalProceduralNode(proceduralNode, ctx, outPose);
      return;
    }
    throw new IllegalArgumentException("Unsupported BlendNode: " + node.getClass().getName());
  }

  private void evalClipNode(final ClipNode node, final EvalContext ctx, final PoseBuffer outPose) {
    final ClipId clipId = node.clipId();
    final Clip clip = ctx.clips().get(clipId);
    if (clip == null) {
      throw new IllegalArgumentException("Clip not found for clipId: " + clipId.value());
    }
    final float time = ctx.clipTimes().getOrDefault(clipId, 0f) * node.speed();
    final float dt = ctx.floatParams().getOrDefault("animis.deltaSeconds", 0f) * node.speed();
    final float previousTime = time - dt;
    final boolean loop = ctx.clipLoops().getOrDefault(clipId, true);
    final RootMotionDelta delta = this.clipSampler.sample(clip, ctx.skeleton(), time, previousTime, loop, outPose);
    if (ctx.rootMotionAccumulator() != null) {
      ctx.rootMotionAccumulator().add(delta);
    }
  }

  private void evalLerpNode(
      final LerpNode node,
      final EvalContext ctx,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    final PoseBuffer a = scratch.acquire();
    final PoseBuffer b = scratch.acquire();
    evaluateNode(node.a(), ctx, a, scratch);
    evaluateNode(node.b(), ctx, b, scratch);
    final float t = clamp01(ctx.floatParams().getOrDefault(node.parameter(), 0f));
    blendPoses(a, b, t, outPose);
  }

  private void evalOneDNode(
      final OneDNode node,
      final EvalContext ctx,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    if (node.children().isEmpty()) {
      throw new IllegalArgumentException("OneDNode requires at least one child");
    }
    final List<OneDChild> children = sortedChildren(node.children());
    final float x = ctx.floatParams().getOrDefault(node.parameter(), 0f);

    final OneDChild first = children.getFirst();
    if (x <= first.threshold()) {
      evaluateNode(first.node(), ctx, outPose, scratch);
      return;
    }

    final OneDChild last = children.getLast();
    if (x >= last.threshold()) {
      evaluateNode(last.node(), ctx, outPose, scratch);
      return;
    }

    for (int i = 0; i < children.size() - 1; i++) {
      final OneDChild a = children.get(i);
      final OneDChild b = children.get(i + 1);
      if (x < a.threshold() || x > b.threshold()) {
        continue;
      }
      final float span = b.threshold() - a.threshold();
      final float t = span <= 0f ? 0f : clamp01((x - a.threshold()) / span);
      final PoseBuffer poseA = scratch.acquire();
      final PoseBuffer poseB = scratch.acquire();
      evaluateNode(a.node(), ctx, poseA, scratch);
      evaluateNode(b.node(), ctx, poseB, scratch);
      blendPoses(poseA, poseB, t, outPose);
      return;
    }

    evaluateNode(last.node(), ctx, outPose, scratch);
  }

  private void evalAddNode(
      final AddNode node,
      final EvalContext ctx,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    final PoseBuffer base = scratch.acquire();
    final PoseBuffer additive = scratch.acquire();
    evaluateNode(node.base(), ctx, base, scratch);
    evaluateNode(node.additive(), ctx, additive, scratch);
    applyAdditive(base, additive, clamp01(node.weight()), outPose);
  }

  private void evalProceduralNode(
      final ProceduralNode node,
      final EvalContext ctx,
      final PoseBuffer outPose) {
    setAdditiveIdentity(outPose);
    final float timeSeconds = proceduralTimeSeconds(ctx);
    if (node instanceof BreathingNode breathing) {
      evalBreathingNode(breathing, ctx, timeSeconds, outPose);
      return;
    }
    if (node instanceof WeightShiftNode weightShift) {
      evalWeightShiftNode(weightShift, ctx, timeSeconds, outPose);
      return;
    }
    if (node instanceof HeadTurnNode headTurn) {
      evalHeadTurnNode(headTurn, ctx, outPose);
      return;
    }
    throw new IllegalArgumentException("Unsupported ProceduralNode: " + node.getClass().getName());
  }

  private void evalBreathingNode(
      final BreathingNode node,
      final EvalContext ctx,
      final float timeSeconds,
      final PoseBuffer outPose) {
    final int joint = node.spineJoint();
    if (joint < 0 || joint >= outPose.jointCount()) {
      return;
    }
    final float exhaustion = clamp01(ctx.floatParams().getOrDefault(node.exhaustionParameter(), 0f));
    final float cycle = Math.max(1e-4f, node.cycleSeconds());
    final float phase = (float) (2.0 * Math.PI * (timeSeconds / cycle));
    final float angle = node.amplitudeRadians() * exhaustion * (float) Math.sin(phase);
    final float[] q = quatFromAxisAngle(1f, 0f, 0f, angle);
    outPose.setRotation(joint, q[0], q[1], q[2], q[3]);
  }

  private void evalWeightShiftNode(
      final WeightShiftNode node,
      final EvalContext ctx,
      final float timeSeconds,
      final PoseBuffer outPose) {
    final int joint = node.hipJoint();
    if (joint < 0 || joint >= outPose.jointCount()) {
      return;
    }
    final float idleTime = Math.max(0f, ctx.floatParams().getOrDefault(node.idleTimeParameter(), 0f));
    final float cycle = Math.max(1e-4f, node.cycleSeconds());
    final float activation = clamp01(idleTime / cycle);
    final float phase = (float) (2.0 * Math.PI * (timeSeconds / cycle));
    final float shift = node.amplitudeMeters() * activation * (float) Math.sin(phase);
    outPose.setTranslation(joint, shift, 0f, 0f);
  }

  private void evalHeadTurnNode(
      final HeadTurnNode node,
      final EvalContext ctx,
      final PoseBuffer outPose) {
    final int joint = node.headJoint();
    if (joint < 0 || joint >= outPose.jointCount()) {
      return;
    }
    final float targetYaw = clamp(
        ctx.floatParams().getOrDefault(node.targetYawParameter(), 0f),
        -Math.abs(node.maxYawRadians()),
        Math.abs(node.maxYawRadians()));
    final float targetPitch = clamp(
        ctx.floatParams().getOrDefault(node.targetPitchParameter(), 0f),
        -Math.abs(node.maxPitchRadians()),
        Math.abs(node.maxPitchRadians()));

    final float dt = Math.max(0f, ctx.floatParams().getOrDefault("animis.deltaSeconds", 1f / 60f));
    final float maxStep = Math.max(0f, node.trackingSpeed()) * dt;
    final java.util.Map<Integer, float[]> stateByJoint = this.headTrackingState.get();
    final float[] state = stateByJoint.computeIfAbsent(joint, k -> new float[] {0f, 0f});
    state[0] = moveTowards(state[0], targetYaw, maxStep);
    state[1] = moveTowards(state[1], targetPitch, maxStep);

    final float[] qYaw = quatFromAxisAngle(0f, 1f, 0f, state[0]);
    final float[] qPitch = quatFromAxisAngle(1f, 0f, 0f, state[1]);
    final float[] q = mul(
        qYaw[0], qYaw[1], qYaw[2], qYaw[3],
        qPitch[0], qPitch[1], qPitch[2], qPitch[3]);
    outPose.setRotation(joint, q[0], q[1], q[2], q[3]);
  }

  private static List<OneDChild> sortedChildren(final List<OneDChild> children) {
    final ArrayList<OneDChild> copy = new ArrayList<>(children);
    copy.sort(Comparator.comparing(OneDChild::threshold));
    return copy;
  }

  private static void blendPoses(
      final PoseBuffer a,
      final PoseBuffer b,
      final float t,
      final PoseBuffer outPose) {
    final float[] at = a.localTranslations();
    final float[] bt = b.localTranslations();
    final float[] as = a.localScales();
    final float[] bs = b.localScales();
    final float[] ar = a.localRotations();
    final float[] br = b.localRotations();

    for (int i = 0; i < outPose.jointCount(); i++) {
      final int tBase = i * 3;
      outPose.setTranslation(
          i,
          lerp(at[tBase], bt[tBase], t),
          lerp(at[tBase + 1], bt[tBase + 1], t),
          lerp(at[tBase + 2], bt[tBase + 2], t));
      outPose.setScale(
          i,
          lerp(as[tBase], bs[tBase], t),
          lerp(as[tBase + 1], bs[tBase + 1], t),
          lerp(as[tBase + 2], bs[tBase + 2], t));

      final int rBase = i * 4;
      final float[] q = slerp(
          ar[rBase], ar[rBase + 1], ar[rBase + 2], ar[rBase + 3],
          br[rBase], br[rBase + 1], br[rBase + 2], br[rBase + 3],
          t);
      outPose.setRotation(i, q[0], q[1], q[2], q[3]);
    }
  }

  private static void applyAdditive(
      final PoseBuffer base,
      final PoseBuffer additive,
      final float weight,
      final PoseBuffer outPose) {
    final float[] bt = base.localTranslations();
    final float[] at = additive.localTranslations();
    final float[] bs = base.localScales();
    final float[] as = additive.localScales();
    final float[] br = base.localRotations();
    final float[] ar = additive.localRotations();

    for (int i = 0; i < outPose.jointCount(); i++) {
      final int tBase = i * 3;
      outPose.setTranslation(
          i,
          bt[tBase] + at[tBase] * weight,
          bt[tBase + 1] + at[tBase + 1] * weight,
          bt[tBase + 2] + at[tBase + 2] * weight);
      outPose.setScale(
          i,
          bs[tBase] + (as[tBase] - 1f) * weight,
          bs[tBase + 1] + (as[tBase + 1] - 1f) * weight,
          bs[tBase + 2] + (as[tBase + 2] - 1f) * weight);

      final int rBase = i * 4;
      final float[] weightedAdd = slerp(0f, 0f, 0f, 1f, ar[rBase], ar[rBase + 1], ar[rBase + 2], ar[rBase + 3], weight);
      final float[] composed = mul(
          br[rBase], br[rBase + 1], br[rBase + 2], br[rBase + 3],
          weightedAdd[0], weightedAdd[1], weightedAdd[2], weightedAdd[3]);
      outPose.setRotation(i, composed[0], composed[1], composed[2], composed[3]);
    }
  }

  private static float clamp01(final float value) {
    return Math.max(0f, Math.min(1f, value));
  }

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float moveTowards(final float current, final float target, final float maxStep) {
    if (maxStep <= 0f) {
      return current;
    }
    final float delta = target - current;
    if (Math.abs(delta) <= maxStep) {
      return target;
    }
    return current + Math.copySign(maxStep, delta);
  }

  private static float proceduralTimeSeconds(final EvalContext ctx) {
    final Float explicit = ctx.floatParams().get("animis.timeSeconds");
    if (explicit != null) {
      return explicit;
    }
    float max = 0f;
    for (final float t : ctx.clipTimes().values()) {
      max = Math.max(max, t);
    }
    return max;
  }

  private static void setAdditiveIdentity(final PoseBuffer outPose) {
    for (int i = 0; i < outPose.jointCount(); i++) {
      outPose.setTranslation(i, 0f, 0f, 0f);
      outPose.setScale(i, 1f, 1f, 1f);
      outPose.setRotation(i, 0f, 0f, 0f, 1f);
    }
  }

  private static float[] quatFromAxisAngle(final float ax, final float ay, final float az, final float angle) {
    final float lenSq = ax * ax + ay * ay + az * az;
    if (lenSq <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float invLen = 1f / (float) Math.sqrt(lenSq);
    final float half = angle * 0.5f;
    final float s = (float) Math.sin(half);
    return new float[] {ax * invLen * s, ay * invLen * s, az * invLen * s, (float) Math.cos(half)};
  }

  private static float lerp(final float a, final float b, final float t) {
    return a + (b - a) * t;
  }

  private static float[] mul(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    return normalize(
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz);
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
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
      dot = -dot;
    }

    if (dot > 0.9995f) {
      return normalize(
          lerp(ax, bx, t),
          lerp(ay, by, t),
          lerp(az, bz, t),
          lerp(aw, bw, t));
    }

    final float theta0 = (float) Math.acos(dot);
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);

    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalize(
        s0 * ax + s1 * bx,
        s0 * ay + s1 * by,
        s0 * az + s1 * bz,
        s0 * aw + s1 * bw);
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 0f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float invLen = 1f / (float) Math.sqrt(lenSq);
    return new float[] {x * invLen, y * invLen, z * invLen, w * invLen};
  }

  private static final class ScratchPool {
    private final ArrayList<PoseBuffer> buffers;
    private int cursor;
    private int jointCount;

    private ScratchPool(final int initialCapacity) {
      this.buffers = new ArrayList<>(initialCapacity);
      this.cursor = 0;
      this.jointCount = 0;
    }

    private void begin(final int jointCount) {
      this.cursor = 0;
      this.jointCount = jointCount;
      for (int i = 0; i < this.buffers.size(); i++) {
        if (this.buffers.get(i).jointCount() != jointCount) {
          this.buffers.set(i, new PoseBuffer(jointCount));
        }
      }
    }

    private PoseBuffer acquire() {
      if (this.cursor >= this.buffers.size()) {
        this.buffers.add(new PoseBuffer(this.jointCount));
      }
      return this.buffers.get(this.cursor++);
    }
  }
}
