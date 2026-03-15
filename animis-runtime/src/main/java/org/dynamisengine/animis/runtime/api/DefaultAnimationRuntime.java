package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.blend.BlendNode;
import org.dynamisengine.animis.blend.ClipNode;
import org.dynamisengine.animis.blend.LerpNode;
import org.dynamisengine.animis.blend.OneDNode;
import org.dynamisengine.animis.blend.ProceduralNode;
import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.ClipId;
import org.dynamisengine.animis.ik.IkChain;
import org.dynamisengine.animis.runtime.blend.BlendEvaluator;
import org.dynamisengine.animis.runtime.blend.DefaultBlendEvaluator;
import org.dynamisengine.animis.runtime.ik.IkSolver;
import org.dynamisengine.animis.runtime.ik.TwoBoneIkSolver;
import org.dynamisengine.animis.runtime.physics.DefaultPhysicsCharacterController;
import org.dynamisengine.animis.runtime.physics.PhysicsCharacterController;
import org.dynamisengine.animis.runtime.physics.PhysicsCharacterDef;
import org.dynamisengine.animis.runtime.secondary.DefaultSecondaryMotionSolver;
import org.dynamisengine.animis.runtime.secondary.SecondaryMotionSolver;
import org.dynamisengine.animis.runtime.sampling.DefaultClipSampler;
import org.dynamisengine.animis.runtime.skinning.SkinningComputer;
import org.dynamisengine.animis.runtime.state.DefaultStateMachineEvaluator;
import org.dynamisengine.animis.runtime.state.StateMachineEvaluator;
import org.dynamisengine.animis.runtime.state.StateMachineInstance;
import org.dynamisengine.animis.runtime.warp.DefaultPoseWarper;
import org.dynamisengine.animis.runtime.warp.PoseWarper;
import org.dynamisengine.animis.skeleton.Skeleton;
import org.dynamisengine.animis.state.StateDef;
import org.dynamisengine.animis.state.StateMachineDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultAnimationRuntime implements AnimationRuntime {
  private final Map<ClipId, Clip> clips;
  private final Map<ClipId, Boolean> clipLoops;
  private final List<IkChain> ikChains;
  private final SkinningComputer skinningComputer;
  private final IkSolver ikSolver;
  private final PoseWarper poseWarper;
  private final SecondaryMotionSolver secondaryMotionSolver;
  private final PhysicsCharacterController physicsCharacterController;
  private final StateMachineEvaluator stateMachineEvaluator;

  public DefaultAnimationRuntime(
      final Map<ClipId, Clip> clips,
      final Map<ClipId, Boolean> clipLoops,
      final List<IkChain> ikChains,
      final SkinningComputer skinningComputer) {
    this(clips, clipLoops, ikChains, skinningComputer, null);
  }

  public DefaultAnimationRuntime(
      final Map<ClipId, Clip> clips,
      final Map<ClipId, Boolean> clipLoops,
      final List<IkChain> ikChains,
      final SkinningComputer skinningComputer,
      final PhysicsCharacterDef physicsCharacterDef) {
    final BlendEvaluator blendEvaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    this.stateMachineEvaluator = new DefaultStateMachineEvaluator(blendEvaluator);
    this.ikSolver = new TwoBoneIkSolver();
    this.poseWarper = new DefaultPoseWarper();
    this.secondaryMotionSolver = new DefaultSecondaryMotionSolver();
    this.physicsCharacterController = physicsCharacterDef == null ? null : new DefaultPhysicsCharacterController(physicsCharacterDef);
    this.clips = new HashMap<>(clips);
    this.clipLoops = new HashMap<>(clipLoops);
    this.ikChains = ikChains;
    this.skinningComputer = skinningComputer;
  }

  @Override
  public AnimatorInstance create(final StateMachineDef machine, final Skeleton skeleton) {
    validateClipsPresent(machine);
    return new DefaultAnimatorInstance(
        skeleton,
        this.stateMachineEvaluator,
        new StateMachineInstance(machine),
        this.ikSolver,
        this.poseWarper,
        this.secondaryMotionSolver,
        this.physicsCharacterController,
        this.skinningComputer,
        this.ikChains,
        this.clips,
        this.clipLoops);
  }

  private void validateClipsPresent(final StateMachineDef machine) {
    for (final StateDef state : machine.states()) {
      validateNode(state.motion());
    }
  }

  private void validateNode(final BlendNode node) {
    if (node == null) {
      throw new IllegalArgumentException("BlendNode motion cannot be null");
    }
    if (node instanceof ClipNode clipNode) {
      if (!this.clips.containsKey(clipNode.clipId())) {
        throw new IllegalArgumentException("Missing clip for clipId: " + clipNode.clipId().value());
      }
      return;
    }
    if (node instanceof LerpNode lerpNode) {
      validateNode(lerpNode.a());
      validateNode(lerpNode.b());
      return;
    }
    if (node instanceof org.dynamisengine.animis.blend.AddNode addNode) {
      validateNode(addNode.base());
      validateNode(addNode.additive());
      return;
    }
    if (node instanceof OneDNode oneDNode) {
      for (final var child : oneDNode.children()) {
        validateNode(child.node());
      }
      return;
    }
    if (node instanceof ProceduralNode) {
      return;
    }
    throw new IllegalArgumentException("Unsupported BlendNode: " + node.getClass().getName());
  }
}
