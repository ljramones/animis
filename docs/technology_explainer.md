# Animis — Technology Explainer

Animis is a pure-Java skeletal animation library for real-time engines. It covers the full
animation stack — from loading clips off disk to physically-simulated characters — organized as
a strict model/evaluation split with no native dependencies.

This document explains what Animis does, how it works internally, and why the architecture is
designed the way it is. It is intended for engineers integrating Animis into an engine, tools
developers working with animation data, and anyone who wants to understand the library deeply
enough to extend it.

---

## The Problem Animis Solves

A 3D character in a game or simulation is a skeleton — a hierarchy of joints — with a mesh
skinned to it. Making that character move requires solving several distinct problems:

1. **Loading** — reading animation data from files (glTF, BVH) into a usable data model
2. **Sampling** — interpolating keyframe data to produce joint transforms at an arbitrary time
3. **Blending** — combining multiple animations (walk + aim, idle + breathe) into a single pose
4. **State management** — deciding which animations play based on game state, with smooth transitions
5. **Inverse kinematics** — adjusting joint positions to reach targets (foot planting, hand grabs)
6. **Skinning** — computing per-joint matrices that deform the mesh
7. **Secondary motion** — bones that react to physics (hair, cape, loose equipment)
8. **Physics integration** — characters that physically reach toward their animated pose rather
   than snapping to it

Animis solves all of these in a coherent, layered architecture.

---

## Module Structure

```
animis              — immutable data model, no runtime state
animis-runtime      — evaluation pipeline, all runtime computation
animis-loader       — format importers (glTF, BVH)
animis-neural       — ONNX-backed neural pose prediction
animis-perf         — JMH benchmarks and perf gate
animis-demo         — integration examples
```

The critical architectural constraint: **animis has no dependencies**. It is pure data model —
records and sealed interfaces. Tools, asset pipelines, and editors can depend on animis without
pulling in Vectrix, MeshForge, or any evaluation code. animis-runtime depends on animis plus
Vectrix (math) and MeshForge (mesh integration). animis-loader depends on animis and
meshforge-loader (to reuse glTF parsing infrastructure). Neither loader nor neural depends on
animis-runtime. The dependency graph is a strict DAG.

JPMS module descriptors enforce these boundaries at compile time. animis-runtime's internal
evaluator implementations are in org.animis.runtime.internal, which is not exported. Callers
see only interfaces.

---

## The Data Model (animis)

Everything in animis is immutable. Records are the primary building block. There is no mutable
state anywhere in the data model.

### Skeleton

A Skeleton is a named, ordered list of Joints with a designated root joint index.

```
Skeleton
  name: String
  joints: List<Joint>
  rootJoint: int
  secondaryChains: List<SecondaryChainDef>
```

Each Joint carries its index, name, parent index (-1 for root), and a BindTransform — the
joint's rest pose as translation, rotation (quaternion), and scale. The bind transform is the
position of the joint when the mesh was authored. Everything else in the animation system is
expressed as a deviation from or application of this rest pose.

Joint ordering is guaranteed: parents always appear before children. This means a single
forward pass over the joint list in order always produces correct world-space transforms without
needing to sort or recurse.

### Clip

A Clip is a named animation sequence containing per-joint transform tracks.

```
Clip
  id: ClipId
  name: String
  durationSeconds: float
  tracks: List<TransformTrack>
  rootMotion: Optional<RootMotionDef>
  events: List<AnimationEvent>
```

Each TransformTrack covers one joint and stores three flat float arrays — translations (xyz per
sample), rotations (xyzw quaternion per sample), scales (xyz per sample). The arrays are
packed for cache efficiency: all X translations together, not interleaved per-frame structs.

TrackMetadata accompanies each track with the source frame rate, the original curve type hint
(sampled, hermite, bezier), sample count, and quantization parameters. The curve type hint is
metadata only in v1 — the sampler always operates on the sampled arrays — but it is preserved
so spline-backed evaluation can be added later without changing the data model.

Not every joint needs a track. Joints without a track inherit their bind pose during sampling.
This is essential for clips that only animate part of a skeleton — a facial animation clip should
not zero out the body joints.

### Blend Tree

The blend tree defines how animations are combined. It is a sealed type hierarchy:

```
BlendNode (sealed)
  ClipNode       — plays a single clip
  LerpNode       — blends two nodes by a float parameter
  OneDNode       — blends N clips along a 1D axis (speed, direction)
  AddNode        — adds a weighted additive layer on top of a base
  ProceduralNode — generates motion algorithmically, no clip data
    BreathingNode
    WeightShiftNode
    HeadTurnNode
```

Sealed types mean the compiler enforces exhaustive pattern matching. Adding a new node type
requires updating every evaluator — you cannot forget a case.

BlendLayer wraps a BlendNode with a LayerMode (OVERRIDE or ADDITIVE) and a BoneMask.
BoneMask assigns per-joint float weights (0.0 to 1.0), not binary on/off, allowing smooth
transitions between body zones at the shoulder rather than hard cuts.

### State Machine

StateMachineDef is a named graph of states and transitions.

```
StateMachineDef
  name: String
  states: List<StateDef>
  entryState: String

StateDef
  name: String
  motion: BlendNode
  transitions: List<TransitionDef>

TransitionDef
  toState: String
  condition: ConditionExpr
  blendSeconds: float
  halfLife: float           — inertial blending parameter
  hasExitTime: boolean
  exitTimeNormalized: float
```

ConditionExpr is also sealed:

```
ConditionExpr (sealed)
  BoolParam      — parameter == true/false
  FloatCompare   — parameter op threshold (>, <, >=, <=, ==)
  AndExpr        — A && B
  OrExpr         — A || B
  NotExpr        — !A
```

This gives designers a complete logical language for transition conditions without any string
parsing or scripting runtime.

### IK

IkChain defines a two-bone IK target:

```
IkChain
  name: String
  rootJoint: int
  midJoint: int
  tipJoint: int
  poleTargetJoint: Optional<Integer>
  minStretch: float
  maxStretch: float
```

FabrikChainDef extends this for multi-joint chains:

```
FabrikChainDef
  name: String
  joints: List<Integer>   — ordered root to tip
  tolerance: float
  maxIterations: int
  angleLimits: List<float[]>
```

### Motion Database

MotionDatabase is the data structure for motion matching:

```
MotionDatabase
  clips: List<Clip>
  frames: List<MotionFrame>
  schema: MotionFeatureSchema

MotionFrame
  clipIndex: int
  timeSeconds: float
  poseFeatures: float[]       — joint positions/velocities
  trajectoryFeatures: float[] — future trajectory samples
  contactFlags: float[]       — per-foot contact state
```

The feature schema describes which joints contribute to the feature vector, how many trajectory
samples are stored, and the time interval between them. This schema must match the feature
extraction code at query time.

---

## The Evaluation Pipeline (animis-runtime)

The evaluation pipeline runs once per character per frame. The stages in order:

```
Parameters (setBool, setFloat)
        ↓
StateMachineEvaluator.tick()
        ↓
BlendEvaluator.evaluate()  ←  ClipSampler.sample()
        ↓
LayeredBlendEvaluator (multi-layer compositing)
        ↓
TwoBoneIkSolver / FabrikSolver
        ↓
PoseWarper
        ↓
DefaultSecondaryMotionSolver
        ↓
PhysicsCharacterController (optional)
        ↓
SkinningComputer.compute()
        ↓
Pose (immutable snapshot) + SkinningOutput (matrix palette)
```

### PoseBuffer and Pose

PoseBuffer is the mutable working buffer that flows through the pipeline. It stores per-joint
local-space translations, rotations, and scales as flat float arrays. It is sized at Skeleton
construction time and reused across frames — no allocation in the hot path.

Pose is an immutable snapshot produced at the end of each update. Callers receive a Pose from
AnimatorInstance.pose() and can safely hold it across frames. The pipeline writes into PoseBuffer
and snapshots to Pose only at the end of update().

Thread-local scratch buffers are used wherever multiple PoseBuffers are needed simultaneously
(LerpNode evaluation requires two scratch buffers). This eliminates per-frame allocation while
remaining thread-safe.

### Clip Sampling

DefaultClipSampler takes a Clip, Skeleton, time, and loop flag and writes interpolated joint
transforms into a PoseBuffer.

The process:
1. Normalize time — clamp to [0, duration] or wrap modulo duration depending on loop flag
2. Initialize all joints to bind pose — joints without a track in this clip keep their rest
   position, not identity
3. For each TransformTrack, compute the sample index and blend alpha from
   time / sampleIntervalSeconds
4. Linear interpolation on translations and scales
5. Quaternion slerp on rotations, with shortest-path handling (negate if dot product < 0)
   and normalization of the result
6. Write into PoseBuffer at jointIndex

When a clip has a RootMotionDef, the sampler additionally computes the root joint's world-space
delta between the previous time and current time, zeroes those components in the pose (so the
root joint stays at origin in pose space), and returns the delta separately. Loop wrap-around
is handled correctly — a loop boundary delta is computed as (end - prevTime) + (currentTime - 0)
rather than wrapping backwards.

Animation events are detected by scanning the clip's AnimationEvent list for normalized times
that fall between previousTimeSeconds and currentTimeSeconds. Loop boundaries are handled by
splitting the scan into two passes.

When QuantizationSpec.enabled is true on a track's metadata, the sampler decompresses on the
fly. Rotations use smallest-three encoding: the largest quaternion component is dropped, its
index and sign stored in a metadata byte, and the remaining three components stored as 16-bit
signed integers mapped to [-1, 1]. Decompression reconstructs the dropped component from the
unit quaternion constraint. Translations and scales are delta-decoded from their first sample
using the configured step size.

### Blend Tree Evaluation

DefaultBlendEvaluator walks the BlendNode tree recursively and produces a blended PoseBuffer.

**ClipNode** — calls ClipSampler directly. Time is per-clip from EvalContext, multiplied by
ClipNode.speed(). Root motion and events are accumulated into the EvalContext's accumulators.

**LerpNode** — evaluates both child nodes into two scratch PoseBuffers, then blends joint by
joint. Alpha comes from the named float parameter in EvalContext, clamped to [0, 1].
Translations and scales lerp linearly. Rotations slerp with shortest-path handling.

**OneDNode** — finds the two OneDChild entries whose thresholds bracket the current parameter
value, computes blend alpha between them, evaluates both children, blends. Edge cases:
parameter below all thresholds uses the first child only; parameter above all thresholds uses
the last child only.

**AddNode** — evaluates the base node into outPose, evaluates the additive node into a scratch
buffer, then adds the additive delta weighted by AddNode.weight(). Addition in local space: for
translations and scales, add the weighted delta; for rotations, compose the base rotation with
the weighted additive rotation (quaternion multiplication, not lerp).

**ProceduralNode** — generates motion analytically:

BreathingNode produces a sinusoidal rotation oscillation on the designated spine joint.
Amplitude is scaled by the exhaustion parameter — a tired character breathes more heavily.

WeightShiftNode produces lateral translation oscillation on the hip joint. Amplitude is scaled
by idle time — a character standing still gradually shifts their weight.

HeadTurnNode rotates the head joint toward a target yaw and pitch, limited by maxYawRadians
and maxPitchRadians, with velocity clamping at trackingSpeed radians per second so the head
turns smoothly rather than snapping.

Procedural nodes output only the affected joints — all other joints in the output buffer remain
at their base pose. This means procedural nodes are inherently additive and compose cleanly with
any underlying motion.

### Multi-Layer Compositing

LayeredBlendEvaluator processes a list of BlendLayers in order.

The base layer (always OVERRIDE mode, full body) is evaluated first into the output buffer.
Each subsequent layer is evaluated into a scratch buffer, then composited onto the output using
the layer's BoneMask weights:

For OVERRIDE layers: outPose[j] = lerp(outPose[j], layerPose[j], mask.weight(j))
For ADDITIVE layers: outPose[j] = outPose[j] + layerPose[j] * mask.weight(j)

A typical layered setup has a lower body locomotion layer (full body mask), an upper body aim
layer (upper body mask, weights fade through the torso), and a full-body additive breathing
layer. These compose to a character that walks, aims, and breathes simultaneously from three
independent animation clips.

### State Machine Evaluation

DefaultStateMachineEvaluator ticks the StateMachineInstance by dt seconds each frame.

When no transition is active, the evaluator scans the current state's transition list in order.
For each TransitionDef, it evaluates the ConditionExpr against the current parameter map. The
first condition that returns true triggers a transition. The exit time guard is checked first
if hasExitTime is true — the condition cannot fire until the current clip has played past
exitTimeNormalized.

When a transition fires, the evaluator captures the current PoseBuffer as an inertial snapshot
and begins the transition. From this point, each frame evaluates both the outgoing state and
the incoming state, blending between them using inertial blending rather than linear crossfade.

**Inertial blending** works by recording the offset and velocity of each joint at transition
start (x0, v0), then applying a critically-damped spring each frame:

```
omega = 2π / halfLife
x(t) = (x0 + (v0 + omega * x0) * t) * e^(-omega * t)
outPose[j] = targetPose[j] + x(t)
```

As t increases, x(t) converges to zero and the pose converges to the incoming state's motion.
The result is a transition that preserves the character's momentum rather than sliding between
two frozen poses. halfLife is typically 0.1 to 0.2 seconds.

**Transition interruption** allows a new transition to fire before the current one completes.
When interrupted, the evaluator captures the current blended output as a frozen snapshot,
re-initializes the inertial state from the snapshot's velocity to the new target, and begins
the new transition. Back-to-back interruptions do not accumulate drift because each re-capture
starts from the current physical state.

### Inverse Kinematics

**TwoBoneIkSolver** solves two-bone chains analytically using the law of cosines.

The solver:
1. Computes world-space positions of root, mid, and tip joints via a forward kinematics pass
2. Computes upper and lower bone lengths
3. Clamps the target distance to [minStretch, maxStretch] * totalLength
4. Applies the law of cosines to find the mid-joint bend angle
5. Rotates the root joint to aim toward the target
6. If a pole target is present, rotates the bend plane toward it
7. Converts corrected world-space positions back to local-space quaternions
8. Writes into PoseBuffer

The analytical solution is exact and runs in constant time regardless of chain complexity.
It is the correct approach for arms and legs where the joint count is always two.

**FabrikSolver** handles arbitrary-length chains using iterative Forwards And Backwards Reaching
Inverse Kinematics.

Each iteration consists of two passes:

Forward pass (tip to root):
- Move the tip to the target
- For each joint from tip toward root: place it at boneLength along the direction from child to
  current joint

Backward pass (root to tip):
- Move root back to its original position
- For each joint from root toward tip: place it at boneLength along the direction from parent to
  current joint

Iterations repeat until the tip is within tolerance of the target or maxIterations is reached.
Per-joint angle limits are applied during the backward pass. The solver converts final world
positions to local-space quaternions and writes into PoseBuffer.

FABRIK is appropriate for spine chains, tails, tentacles, and any chain longer than two joints.

### Pose Warping

DefaultPoseWarper adjusts joint positions to fit environment geometry at runtime.

For each WarpTarget, the warper:
1. Computes the current joint's world position via FK
2. Computes the delta from current position to the target world position
3. Distributes the delta across the joint chain weighted by distance from the tip
4. Clamps total translation to maxTranslationMeters and rotation adjustment to maxRotationRadians
5. Writes corrected transforms into PoseBuffer

Pose warping runs after IK so that IK targets are already applied. A vault animation can be
warped to fit a 1.3m obstacle when it was authored for 1.0m without re-authoring.

### Secondary Motion

DefaultSecondaryMotionSolver applies spring dynamics to bones not driven by keyframe animation.

For each SecondaryChainDef, for each joint in the chain:
1. Compute the target position from the current animated pose
2. Apply spring force toward target: F = stiffness * (target - current)
3. Apply damping: F -= damping * velocity
4. Integrate velocity and position
5. Clamp to angularLimit radians from the animated pose
6. Write corrected rotation into PoseBuffer

Secondary motion runs after pose warping and before skinning. A cape's chains respond to the
character's movement; hair swings as the head turns; loose equipment lags behind fast actions.

### Physics Character Controller

DefaultPhysicsCharacterController integrates DynamisCollision to drive joints via physics
rather than keyframe assignment.

The AnimatorInstance evaluates a target pose through the normal pipeline. The controller
maintains a per-joint rigid body (via RigidBodyAdapter3D) for each joint in drivenJoints.
Each frame:

1. Read target joint world transform from the target pose
2. Compute drive torque: torque = stiffness * (targetRotation - currentRotation)
3. Apply damping: torque -= damping * angularVelocity
4. Clamp to maxTorque
5. Feed torque into DynamisCollision's ContactSolver3D
6. Step simulation via PhysicsStep3D
7. Read simulated joint transforms back into simulatedPose

Joints in keyframedJoints (fingers, face joints) bypass the physics solver entirely and use
the target pose directly. This prevents physics instability in high-degree-of-freedom regions
where the spring model would require impractical stiffness values.

The result: the character physically reaches toward their animated pose. External forces
(collision response, ragdoll impulses) deflect the character away from the target and the
spring drive naturally recovers. This is the Euphoria-style physics animation model — keyframe
animation as a control signal rather than a final answer.

### Skinning

DefaultSkinningComputer computes the joint matrix palette for GPU upload using linear blend
skinning (LBS).

The computation:
1. For each joint in hierarchy order (parent before child, guaranteed by Skeleton ordering):
    - Compute local TRS matrix from the Pose's translation, rotation, scale at that joint index
    - Multiply by parent's world matrix (or identity for root)
    - Store as worldMatrix[i]
2. For each joint:
    - skinningMatrix[i] = worldMatrix[i] * inverseBindMatrix[i]
3. Output: flat float[] of 16 floats per joint, column-major, ready for GPU uniform buffer

Inverse bind matrices are computed once from BindTransform at construction and cached. They are
not recomputed per frame.

The SkinningComputer interface is pluggable — dual quaternion skinning (DQS) can replace LBS
as an alternative implementation for characters where volume preservation under rotation is
important (thick arms, torso twists).

---

## Motion Matching

DefaultMotionMatcher implements nearest-neighbor search over a MotionDatabase.

Given a MotionQuery (current pose features, desired trajectory, contact flags), the matcher
scans all frames in the database and computes a weighted squared distance for each:

```
cost(frame) =
  w_pose * |query.poseFeatures - frame.poseFeatures|² +
  w_traj * |query.trajectoryFeatures - frame.trajectoryFeatures|² +
  w_contact * |query.contactFlags - frame.contactFlags|²
```

The frame with minimum cost is returned. Weight arrays are provided at construction and default
to uniform 1.0 weighting.

On a database of 10,000 frames, the search runs in under 0.5ms because the feature vectors are
small (typically 30-60 floats) and the computation is a tight inner product loop that the JIT
optimizes aggressively.

Motion matching eliminates the state machine and blend tree entirely for locomotion. The
designer authors motion capture clips and annotates them with feature data. The runtime
continuously finds the best next frame — no transition tuning, no blend tree authoring, no
state explosion as movement complexity grows.

---

## Neural Pose Prediction (animis-neural)

OnnxNeuralPosePredictor wraps an ONNX Runtime inference session for learned motion prediction.

A small neural network (typically 4 fully-connected layers, ~100K parameters) takes the current
pose features and velocity features as input and predicts the next pose and trajectory directly.
No database search, no blending — pure inference in under 1ms.

The model is loaded from a Path, InputStream, or byte array at construction. Session options
are configured for single-threaded CPU inference with latency optimization. Input dimensions
are validated against NeuralModelInfo at construction so shape mismatches surface at load time
rather than at inference time.

The training pipeline is outside Animis's scope — models are trained in Python with PyTorch or
similar and exported to ONNX format. Animis defines the inference interface and the expected
input/output tensor shapes.

---

## Animation Loading (animis-loader)

### glTF

GltfAnimationLoader reads the glTF JSON structure and produces AnimationLoadResult containing
Skeletons and Clips.

**Skeleton construction** reads skins[].joints (ordered node indices), nodes[].name and
nodes[].children (hierarchy), and skins[].inverseBindMatrices (accessor of MAT4 floats).
The parent index for each joint is determined by inverting the children relationship — each
node's parent is the joint whose children array contains it. Inverse bind matrices are
decomposed into BindTransform (translation, rotation, scale).

**Clip construction** reads animations[].samplers (input/output accessor pairs with
interpolation mode) and animations[].channels (sampler index, target node, target path).
Channel target paths map to track types: "translation", "rotation", "scale". The target node
is mapped to a joint index via the skin's joint list. Input accessors produce the keyframe time
array; output accessors produce the transform value array. TrackMetadata is populated from the
accessor data and interpolation mode.

Accessor reading reuses meshforge-loader's buffer/bufferView infrastructure — the binary layout
is identical to mesh attribute data.

**Validation** catches: missing skin references, channel target nodes not in the skin's joint
list, accessor type mismatches (rotations must be VEC4, translations VEC3), and inconsistent
sample counts between input and output accessors.

### BVH

BvhAnimationLoader parses BVH's two-section text format.

The HIERARCHY section is parsed recursively. Each JOINT block yields a joint with its OFFSET
(bind translation) and CHANNELS specification. The channel order (ZXY, XYZ etc.) varies per
joint and is recorded for frame data parsing. ROOT and End Site are handled as special cases.

The MOTION section reads frame count, frame time, and N lines of channel values. Channel values
are distributed to the correct joint tracks based on the channel mapping from the hierarchy
pass. Euler angles are converted to quaternions during load using the joint's specified channel
order — no runtime Euler-to-quaternion conversion needed.

---

## Performance

Benchmark results on JDK 25 (non-forked, indicative):

| Benchmark | Result | Target |
|---|---|---|
| AnimatorInstance.update() — 100 joints | 0.009 ms/op | ≤ 1.000 ms |
| MotionMatcher.findBest() — 10K frames | 0.213 ms/op | ≤ 0.500 ms |
| ClipSampler.sample() — 60s clip | 0.001 ms/op | ≤ 0.300 ms |
| SkinningComputer.compute() — 100 joints | 0.007 ms/op | ≤ 0.300 ms |

All hot paths avoid allocation. PoseBuffer is reused across frames. Thread-local scratch
buffers eliminate allocation in the blend tree evaluator. Inverse bind matrices are cached per
skeleton. The JIT optimizes the tight inner loops in motion matching and skinning aggressively
on uniform float array operations.

---

## Known Gaps and Roadmap

**animis-loader:**
- GLB (binary glTF) not yet supported — GltfAnimationLoader handles JSON glTF only
- FBX not supported — proprietary format, no open specification

**animis-runtime:**
- Dual quaternion skinning not yet implemented — LBS is the only skinning mode
- FABRIK angle limits use simple bend-angle semantics — twist limits not yet supported
- Neural pose prediction requires externally trained ONNX models — training pipeline is out of scope

**animis:**
- No blend tree authoring format — trees are constructed programmatically
- No animation compression beyond quantization — delta compression not yet implemented

---

## Stack Context

Animis sits at the top of a layered engine stack:

```
Vectrix          — SIMD-optimized math: vectors, matrices, quaternions, noise, color science
MeshForge        — mesh processing: loading, ops, packing, morph targets, skinned mesh
DynamisCollision — collision detection: broadphase, GJK/EPA, contact solving, CCD
Animis           — skeletal animation: sampling, blending, IK, skinning, physics characters
```

No library in this stack depends on any layer above it. Animis depends on Vectrix and MeshForge
but not DynamisCollision — the physics character controller uses DynamisCollision types through
the RigidBodyAdapter3D contract, keeping the dependency in animis-runtime where it belongs and
not polluting the animis data model.