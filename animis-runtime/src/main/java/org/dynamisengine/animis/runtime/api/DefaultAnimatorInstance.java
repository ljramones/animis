package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.clip.ClipId;
import org.dynamisengine.animis.ik.IkChain;
import org.dynamisengine.animis.runtime.blend.EvalContext;
import org.dynamisengine.animis.runtime.blend.EventAccumulator;
import org.dynamisengine.animis.runtime.blend.RootMotionAccumulator;
import org.dynamisengine.animis.runtime.ik.IkSolver;
import org.dynamisengine.animis.runtime.ik.IkTarget;
import org.dynamisengine.animis.runtime.pose.Pose;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.runtime.physics.PhysicsCharacterController;
import org.dynamisengine.animis.runtime.secondary.SecondaryMotionSolver;
import org.dynamisengine.animis.runtime.skinning.SkinningComputer;
import org.dynamisengine.animis.runtime.skinning.SkinningOutput;
import org.dynamisengine.animis.runtime.state.StateMachineEvaluator;
import org.dynamisengine.animis.runtime.state.StateMachineInstance;
import org.dynamisengine.animis.runtime.warp.PoseWarper;
import org.dynamisengine.animis.skeleton.Skeleton;
import org.dynamisengine.animis.warp.WarpTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultAnimatorInstance implements AnimatorInstance {
  private final Skeleton skeleton;
  private final StateMachineEvaluator stateMachineEvaluator;
  private final StateMachineInstance stateMachineInstance;
  private final IkSolver ikSolver;
  private final PoseWarper poseWarper;
  private final SecondaryMotionSolver secondaryMotionSolver;
  private final PhysicsCharacterController physicsCharacterController;
  private final SkinningComputer skinningComputer;
  private final Map<String, IkChain> ikChainsByName;

  private final Map<ClipId, org.dynamisengine.animis.clip.Clip> clips;
  private final Map<ClipId, Float> clipTimes;
  private final Map<ClipId, Boolean> clipLoops;
  private final Map<String, Boolean> boolParams;
  private final Map<String, Float> floatParams;
  private final Map<String, IkTarget> ikTargets;
  private final Map<String, WarpTarget> warpTargets;
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
      final PoseWarper poseWarper,
      final SecondaryMotionSolver secondaryMotionSolver,
      final PhysicsCharacterController physicsCharacterController,
      final SkinningComputer skinningComputer,
      final List<IkChain> ikChains,
      final Map<ClipId, org.dynamisengine.animis.clip.Clip> clips,
      final Map<ClipId, Boolean> clipLoops) {
    this.skeleton = skeleton;
    this.stateMachineEvaluator = stateMachineEvaluator;
    this.stateMachineInstance = stateMachineInstance;
    this.ikSolver = ikSolver;
    this.poseWarper = poseWarper;
    this.secondaryMotionSolver = secondaryMotionSolver;
    this.physicsCharacterController = physicsCharacterController;
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
    this.warpTargets = new HashMap<>();
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
  public void setWarpTarget(final WarpTarget target) {
    if (target == null) {
      throw new IllegalArgumentException("target cannot be null");
    }
    if (target.joint() < 0 || target.joint() >= this.skeleton.joints().size()) {
      throw new IllegalArgumentException("Warp target joint out of bounds: " + target.joint());
    }
    this.warpTargets.put(target.name(), target);
  }

  @Override
  public void clearWarpTarget(final String name) {
    this.warpTargets.remove(name);
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

    if (this.poseWarper != null && !this.warpTargets.isEmpty()) {
      this.poseWarper.warp(this.poseBuffer, this.skeleton, List.copyOf(this.warpTargets.values()));
    }

    if (this.secondaryMotionSolver != null) {
      this.secondaryMotionSolver.solve(this.poseBuffer, this.skeleton, Math.max(0f, deltaSeconds));
    }

    if (this.physicsCharacterController != null) {
      this.physicsCharacterController.update(this.poseBuffer, this.skeleton, Math.max(0f, deltaSeconds));
      final PoseBuffer simulated = this.physicsCharacterController.simulatedPose();
      if (simulated != null) {
        copyPose(simulated, this.poseBuffer);
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

  private static void copyPose(final PoseBuffer source, final PoseBuffer destination) {
    System.arraycopy(source.localTranslations(), 0, destination.localTranslations(), 0, destination.localTranslations().length);
    System.arraycopy(source.localRotations(), 0, destination.localRotations(), 0, destination.localRotations().length);
    System.arraycopy(source.localScales(), 0, destination.localScales(), 0, destination.localScales().length);
  }
}
