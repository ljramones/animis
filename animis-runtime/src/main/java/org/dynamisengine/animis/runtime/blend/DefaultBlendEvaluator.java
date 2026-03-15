package org.dynamisengine.animis.runtime.blend;

import org.dynamisengine.animis.blend.AddNode;
import org.dynamisengine.animis.blend.BlendNode;
import org.dynamisengine.animis.blend.BreathingNode;
import org.dynamisengine.animis.blend.ClipNode;
import org.dynamisengine.animis.blend.HeadTurnNode;
import org.dynamisengine.animis.blend.LerpNode;
import org.dynamisengine.animis.blend.OneDChild;
import org.dynamisengine.animis.blend.OneDNode;
import org.dynamisengine.animis.blend.ProceduralNode;
import org.dynamisengine.animis.blend.WeightShiftNode;
import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.ClipId;
import org.dynamisengine.animis.runtime.api.RootMotionDelta;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.runtime.sampling.ClipSampleResult;
import org.dynamisengine.animis.runtime.sampling.ClipSampler;
import org.dynamisengine.vectrix.core.Quaternionf;
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
    final ClipSampleResult result = this.clipSampler.sample(clip, ctx.skeleton(), time, previousTime, loop, outPose);
    final RootMotionDelta delta = result.rootMotionDelta();
    if (ctx.rootMotionAccumulator() != null) {
      ctx.rootMotionAccumulator().add(delta);
    }
    if (ctx.eventAccumulator() != null) {
      ctx.eventAccumulator().addAll(result.firedEvents());
    }
  }

  private void evalLerpNode(
      final LerpNode node,
      final EvalContext ctx,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    final PoseBuffer a = scratch.acquire();
    final PoseBuffer b = scratch.acquire();
    final EvalContext sideEffectFree = ctx.withoutAccumulators();
    evaluateNode(node.a(), sideEffectFree, a, scratch);
    evaluateNode(node.b(), sideEffectFree, b, scratch);
    final float t = clamp01(ctx.floatParams().getOrDefault(node.parameter(), 0f));
    blendPoses(a, b, t, outPose, scratch);
    accumulateWeightedClipEvents(node.a(), node.b(), t, ctx);
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
      final EvalContext sideEffectFree = ctx.withoutAccumulators();
      evaluateNode(a.node(), sideEffectFree, poseA, scratch);
      evaluateNode(b.node(), sideEffectFree, poseB, scratch);
      blendPoses(poseA, poseB, t, outPose, scratch);
      accumulateWeightedClipEvents(a.node(), b.node(), t, ctx);
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
    final EvalContext sideEffectFree = ctx.withoutAccumulators();
    evaluateNode(node.base(), sideEffectFree, base, scratch);
    evaluateNode(node.additive(), sideEffectFree, additive, scratch);
    applyAdditive(base, additive, clamp01(node.weight()), outPose, scratch);
  }

  private void accumulateWeightedClipEvents(
      final BlendNode a,
      final BlendNode b,
      final float weightB,
      final EvalContext ctx) {
    if (ctx.eventAccumulator() == null) {
      return;
    }
    if (!(a instanceof ClipNode clipA) || !(b instanceof ClipNode clipB)) {
      return;
    }
    final Clip ca = ctx.clips().get(clipA.clipId());
    final Clip cb = ctx.clips().get(clipB.clipId());
    if (ca == null || cb == null || ca.events().isEmpty() || cb.events().isEmpty()) {
      return;
    }
    final float wB = clamp01(weightB);
    final float wA = 1f - wB;
    final float dt = ctx.floatParams().getOrDefault("animis.deltaSeconds", 0f);
    final float currA = normalizedClipTime(ctx, clipA.clipId(), clipA.speed());
    final float prevA = normalizedClipTimeAt(ctx, clipA.clipId(), clipA.speed(), -dt);
    final float currB = normalizedClipTime(ctx, clipB.clipId(), clipB.speed());
    final float prevB = normalizedClipTimeAt(ctx, clipB.clipId(), clipB.speed(), -dt);
    final float curr = wA * currA + wB * currB;
    final float prev = wA * prevA + wB * prevB;

    final java.util.Map<String, Float> weightedEventTimes = new java.util.HashMap<>();
    for (final var event : ca.events()) {
      weightedEventTimes.put(event.name(), event.normalizedTime() * wA);
    }
    for (final var event : cb.events()) {
      weightedEventTimes.merge(event.name(), event.normalizedTime() * wB, Float::sum);
    }
    for (final var entry : weightedEventTimes.entrySet()) {
      final float t = clamp01(entry.getValue());
      if (crossed(prev, curr, t)) {
        ctx.eventAccumulator().add(entry.getKey());
      }
    }
  }

  private static boolean crossed(final float prev, final float curr, final float eventTime) {
    if (curr >= prev) {
      return eventTime > prev && eventTime <= curr;
    }
    return (eventTime > prev && eventTime <= 1f) || (eventTime >= 0f && eventTime <= curr);
  }

  private static float normalizedClipTime(final EvalContext ctx, final ClipId clipId, final float speed) {
    return normalizedClipTimeAt(ctx, clipId, speed, 0f);
  }

  private static float normalizedClipTimeAt(
      final EvalContext ctx,
      final ClipId clipId,
      final float speed,
      final float offsetSeconds) {
    final Clip clip = ctx.clips().get(clipId);
    if (clip == null || clip.durationSeconds() <= 0f) {
      return 0f;
    }
    final boolean loop = ctx.clipLoops().getOrDefault(clipId, true);
    float t = (ctx.clipTimes().getOrDefault(clipId, 0f) + offsetSeconds) * speed;
    final float d = clip.durationSeconds();
    if (!loop) {
      t = clamp(t, 0f, d);
      return t / d;
    }
    final float wrapped = t % d;
    final float n = wrapped < 0f ? wrapped + d : wrapped;
    return n / d;
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
    final Quaternionf q = new Quaternionf().rotateX(angle);
    outPose.setRotation(joint, q.x, q.y, q.z, q.w);
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

    final Quaternionf q = new Quaternionf().rotateY(state[0]).rotateX(state[1]);
    outPose.setRotation(joint, q.x, q.y, q.z, q.w);
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
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    final float[] at = a.localTranslations();
    final float[] bt = b.localTranslations();
    final float[] as = a.localScales();
    final float[] bs = b.localScales();
    final float[] ar = a.localRotations();
    final float[] br = b.localRotations();
    final Quaternionf qa = scratch.quatA;
    final Quaternionf qb = scratch.quatB;
    final Quaternionf qOut = scratch.quatOut;

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
      qa.set(ar[rBase], ar[rBase + 1], ar[rBase + 2], ar[rBase + 3]);
      qb.set(br[rBase], br[rBase + 1], br[rBase + 2], br[rBase + 3]);
      qa.slerp(qb, t, qOut).normalize();
      outPose.setRotation(i, qOut.x, qOut.y, qOut.z, qOut.w);
    }
  }

  private static void applyAdditive(
      final PoseBuffer base,
      final PoseBuffer additive,
      final float weight,
      final PoseBuffer outPose,
      final ScratchPool scratch) {
    final float[] bt = base.localTranslations();
    final float[] at = additive.localTranslations();
    final float[] bs = base.localScales();
    final float[] as = additive.localScales();
    final float[] br = base.localRotations();
    final float[] ar = additive.localRotations();
    final Quaternionf identity = scratch.quatA;
    final Quaternionf addQ = scratch.quatB;
    final Quaternionf weightedAdd = scratch.quatOut;
    final Quaternionf baseQ = new Quaternionf();

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
      identity.set(0f, 0f, 0f, 1f);
      addQ.set(ar[rBase], ar[rBase + 1], ar[rBase + 2], ar[rBase + 3]);
      identity.slerp(addQ, weight, weightedAdd).normalize();
      baseQ.set(br[rBase], br[rBase + 1], br[rBase + 2], br[rBase + 3]);
      baseQ.mul(weightedAdd, weightedAdd).normalize();
      outPose.setRotation(i, weightedAdd.x, weightedAdd.y, weightedAdd.z, weightedAdd.w);
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

  private static float lerp(final float a, final float b, final float t) {
    return a + (b - a) * t;
  }

  private static final class ScratchPool {
    private final ArrayList<PoseBuffer> buffers;
    private final Quaternionf quatA = new Quaternionf();
    private final Quaternionf quatB = new Quaternionf();
    private final Quaternionf quatOut = new Quaternionf();
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
