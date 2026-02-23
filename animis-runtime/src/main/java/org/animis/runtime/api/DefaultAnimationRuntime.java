package org.animis.runtime.api;

import org.animis.blend.BlendNode;
import org.animis.blend.ClipNode;
import org.animis.blend.LerpNode;
import org.animis.blend.OneDNode;
import org.animis.blend.ProceduralNode;
import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.ik.IkChain;
import org.animis.runtime.blend.BlendEvaluator;
import org.animis.runtime.blend.DefaultBlendEvaluator;
import org.animis.runtime.ik.IkSolver;
import org.animis.runtime.ik.TwoBoneIkSolver;
import org.animis.runtime.secondary.DefaultSecondaryMotionSolver;
import org.animis.runtime.secondary.SecondaryMotionSolver;
import org.animis.runtime.sampling.DefaultClipSampler;
import org.animis.runtime.skinning.SkinningComputer;
import org.animis.runtime.state.DefaultStateMachineEvaluator;
import org.animis.runtime.state.StateMachineEvaluator;
import org.animis.runtime.state.StateMachineInstance;
import org.animis.skeleton.Skeleton;
import org.animis.state.StateDef;
import org.animis.state.StateMachineDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultAnimationRuntime implements AnimationRuntime {
  private final Map<ClipId, Clip> clips;
  private final Map<ClipId, Boolean> clipLoops;
  private final List<IkChain> ikChains;
  private final SkinningComputer skinningComputer;
  private final IkSolver ikSolver;
  private final SecondaryMotionSolver secondaryMotionSolver;
  private final StateMachineEvaluator stateMachineEvaluator;

  public DefaultAnimationRuntime(
      final Map<ClipId, Clip> clips,
      final Map<ClipId, Boolean> clipLoops,
      final List<IkChain> ikChains,
      final SkinningComputer skinningComputer) {
    final BlendEvaluator blendEvaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    this.stateMachineEvaluator = new DefaultStateMachineEvaluator(blendEvaluator);
    this.ikSolver = new TwoBoneIkSolver();
    this.secondaryMotionSolver = new DefaultSecondaryMotionSolver();
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
        this.secondaryMotionSolver,
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
    if (node instanceof org.animis.blend.AddNode addNode) {
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
