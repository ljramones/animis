package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.ik.IkChain;
import dev.ljramones.animis.runtime.blend.EvalContext;
import dev.ljramones.animis.runtime.blend.EventAccumulator;
import dev.ljramones.animis.runtime.blend.RootMotionAccumulator;
import dev.ljramones.animis.runtime.ik.IkSolver;
import dev.ljramones.animis.runtime.ik.IkTarget;
import dev.ljramones.animis.runtime.pose.Pose;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.runtime.skinning.SkinningComputer;
import dev.ljramones.animis.runtime.skinning.SkinningOutput;
import dev.ljramones.animis.runtime.state.StateMachineEvaluator;
import dev.ljramones.animis.runtime.state.StateMachineInstance;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultAnimatorInstance implements AnimatorInstance {
  private final Skeleton skeleton;
  private final StateMachineEvaluator stateMachineEvaluator;
  private final StateMachineInstance stateMachineInstance;
  private final IkSolver ikSolver;
  private final SkinningComputer skinningComputer;
  private final Map<String, IkChain> ikChainsByName;

  private final Map<ClipId, dev.ljramones.animis.clip.Clip> clips;
  private final Map<ClipId, Float> clipTimes;
  private final Map<ClipId, Boolean> clipLoops;
  private final Map<String, Boolean> boolParams;
  private final Map<String, Float> floatParams;
  private final Map<String, IkTarget> ikTargets;
  private final Map<String, Runnable> eventListeners;

  private final PoseBuffer poseBuffer;
  private Pose lastPose;
  private SkinningOutput lastSkinningOutput;
  private RootMotionDelta lastRootMotionDelta;

  public DefaultAnimatorInstance(
      final Skeleton skeleton,
      final StateMachineEvaluator stateMachineEvaluator,
      final StateMachineInstance stateMachineInstance,
      final IkSolver ikSolver,
      final SkinningComputer skinningComputer,
      final List<IkChain> ikChains,
      final Map<ClipId, dev.ljramones.animis.clip.Clip> clips,
      final Map<ClipId, Boolean> clipLoops) {
    this.skeleton = skeleton;
    this.stateMachineEvaluator = stateMachineEvaluator;
    this.stateMachineInstance = stateMachineInstance;
    this.ikSolver = ikSolver;
    this.skinningComputer = skinningComputer;
    this.ikChainsByName = new HashMap<>();
    for (final IkChain chain : ikChains) {
      this.ikChainsByName.put(chain.name(), chain);
    }
    this.clips = new HashMap<>(clips);
    this.clipLoops = new HashMap<>(clipLoops);
    this.clipTimes = new HashMap<>();
    for (final ClipId clipId : clips.keySet()) {
      this.clipTimes.put(clipId, 0f);
      this.clipLoops.putIfAbsent(clipId, true);
    }
    this.boolParams = new HashMap<>();
    this.floatParams = new HashMap<>();
    this.ikTargets = new HashMap<>();
    this.eventListeners = new HashMap<>();
    this.poseBuffer = new PoseBuffer(skeleton.joints().size());
    this.lastPose = this.poseBuffer.toPose();
    this.lastSkinningOutput = null;
    this.lastRootMotionDelta = RootMotionDelta.ZERO;
  }

  @Override
  public void setBool(final String name, final boolean value) {
    this.boolParams.put(name, value);
  }

  @Override
  public void setFloat(final String name, final float value) {
    this.floatParams.put(name, value);
  }

  @Override
  public void setIkTarget(final String chainName, final IkTarget target) {
    if (!this.ikChainsByName.containsKey(chainName)) {
      throw new IllegalArgumentException("Unknown IK chain: " + chainName);
    }
    this.ikTargets.put(chainName, target);
  }

  @Override
  public void setEventListener(final String eventName, final Runnable listener) {
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    this.eventListeners.put(eventName, listener);
  }

  @Override
  public void clearEventListener(final String eventName) {
    this.eventListeners.remove(eventName);
  }

  @Override
  public void update(final float deltaSeconds) {
    for (final Map.Entry<ClipId, Float> entry : this.clipTimes.entrySet()) {
      entry.setValue(entry.getValue() + Math.max(0f, deltaSeconds));
    }

    this.floatParams.put("animis.deltaSeconds", Math.max(0f, deltaSeconds));
    final RootMotionAccumulator rootMotionAccumulator = new RootMotionAccumulator();
    final EventAccumulator eventAccumulator = new EventAccumulator();
    final EvalContext ctx = new EvalContext(
        this.skeleton,
        this.clips,
        this.clipTimes,
        this.clipLoops,
        this.boolParams,
        this.floatParams,
        rootMotionAccumulator,
        eventAccumulator);

    this.stateMachineEvaluator.tick(this.stateMachineInstance, deltaSeconds, ctx, this.poseBuffer);

    if (this.ikSolver != null) {
      for (final Map.Entry<String, IkTarget> entry : this.ikTargets.entrySet()) {
        final IkChain chain = this.ikChainsByName.get(entry.getKey());
        if (chain != null) {
          this.ikSolver.solve(this.poseBuffer, this.skeleton, chain, entry.getValue());
        }
      }
    }

    this.lastPose = this.poseBuffer.toPose();
    if (this.skinningComputer != null) {
      this.lastSkinningOutput = this.skinningComputer.compute(this.skeleton, this.lastPose);
    }
    this.lastRootMotionDelta = rootMotionAccumulator.snapshot();
    for (final String eventName : eventAccumulator.snapshot()) {
      final Runnable listener = this.eventListeners.get(eventName);
      if (listener != null) {
        listener.run();
      }
    }
  }

  @Override
  public Pose pose() {
    return this.lastPose;
  }

  @Override
  public SkinningOutput skinningOutput() {
    return this.lastSkinningOutput;
  }

  @Override
  public RootMotionDelta rootMotionDelta() {
    return this.lastRootMotionDelta;
  }
}
