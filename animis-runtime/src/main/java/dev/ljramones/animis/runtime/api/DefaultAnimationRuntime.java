package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.blend.BlendNode;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.LerpNode;
import dev.ljramones.animis.blend.OneDNode;
import dev.ljramones.animis.blend.ProceduralNode;
import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.ik.IkChain;
import dev.ljramones.animis.runtime.blend.BlendEvaluator;
import dev.ljramones.animis.runtime.blend.DefaultBlendEvaluator;
import dev.ljramones.animis.runtime.ik.IkSolver;
import dev.ljramones.animis.runtime.ik.TwoBoneIkSolver;
import dev.ljramones.animis.runtime.sampling.DefaultClipSampler;
import dev.ljramones.animis.runtime.skinning.SkinningComputer;
import dev.ljramones.animis.runtime.state.DefaultStateMachineEvaluator;
import dev.ljramones.animis.runtime.state.StateMachineEvaluator;
import dev.ljramones.animis.runtime.state.StateMachineInstance;
import dev.ljramones.animis.skeleton.Skeleton;
import dev.ljramones.animis.state.StateDef;
import dev.ljramones.animis.state.StateMachineDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultAnimationRuntime implements AnimationRuntime {
  private final Map<ClipId, Clip> clips;
  private final Map<ClipId, Boolean> clipLoops;
  private final List<IkChain> ikChains;
  private final SkinningComputer skinningComputer;
  private final IkSolver ikSolver;
  private final StateMachineEvaluator stateMachineEvaluator;

  public DefaultAnimationRuntime(
      final Map<ClipId, Clip> clips,
      final Map<ClipId, Boolean> clipLoops,
      final List<IkChain> ikChains,
      final SkinningComputer skinningComputer) {
    final BlendEvaluator blendEvaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    this.stateMachineEvaluator = new DefaultStateMachineEvaluator(blendEvaluator);
    this.ikSolver = new TwoBoneIkSolver();
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
    if (node instanceof dev.ljramones.animis.blend.AddNode addNode) {
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
