# Animis — Full Feature Progression

---

## V1 — The Correct Foundation

**Goal:** A production-quality pose evaluation pipeline. Everything above this layer depends on getting this right.

### Clip Sampling
Fixed-interval keyframe sampling with per-joint translation/rotation/scale tracks. Linear interpolation on translations and scales, spherical linear interpolation (slerp) on quaternions. Loop wrap-around handled by the sampler. Bind pose fallback for untracked joints — joints not driven by a clip output their bind transform, not identity. Without this, unanimated joints collapse to origin. Source metadata preserved on every track via `TrackMetadata` so spline-backed tracks can slot in later without breaking the data model.

### Blend Tree Evaluation
Single-layer blend tree with four node types: `ClipNode` (direct clip playback), `LerpNode` (two-way blend by float parameter), `OneDNode` (1D blend space — walk/run speed axis, multiple clips blended by threshold), `AddNode` (additive overlay with weight). `LayerMode.OVERRIDE` and `LayerMode.ADDITIVE` declared and enforced at the layer level even though only one active layer ships in V1. `BoneMask` declared so upper/lower body splits are expressible even if only the single-layer evaluator is active.

### State Machine
Condition-based transitions between named states. Each state owns a `BlendNode` graph. Transitions carry blend duration, optional exit time (play to N% before allowing transition), and a `ConditionExpr` — sealed type covering `BoolParam`, `FloatCompare`, `AndExpr`, `OrExpr`, `NotExpr`. State machine evaluator runs a cross-fade during transitions — the outgoing state continues evaluating while the incoming state blends in over `blendSeconds`. No transition interruption in V1 — once a transition starts it completes.

### Two-Bone Analytical IK
Exact closed-form solution for arm and leg chains. Root joint, mid joint, tip joint. Optional pole target joint to control the plane of rotation (knee facing forward, elbow facing back). Stretch clamping with configurable `minStretch` / `maxStretch` — the chain can lengthen slightly to reach a target before giving up. Solver writes directly into `PoseBuffer` in local space before skinning runs. FABRIK solver declared as a future `IkSolver` implementation but not shipped.

### Skinning Output
Linear blend skinning (LBS). Takes `Skeleton` + evaluated `Pose`, computes per-joint world-space matrices, multiplies by inverse bind pose, outputs a flat `float[]` joint matrix palette ready to upload to a GPU uniform buffer or pass to the renderer. `SkinningComputer` interface is pluggable — dual quaternion skinning (DQS) can replace LBS without changing the caller.

### Pose Pipeline Integration
`AnimatorInstance` ties it all together. `update(float dt)` ticks the state machine, evaluates the blend tree, runs IK, outputs a `Pose`. `pose()` returns an immutable snapshot. Parameters set via `setBool` / `setFloat`. Full pipeline runs in under 1ms per character for typical skeleton sizes (50-100 joints) as a perf gate target.

### Validation and Testing
Data model validation on construction — joint index ranges, track length consistency, clip duration vs sample count, IK chain joint ordering. Stress suite covering degenerate skeletons (single joint, 200 joints), zero-duration clips, all-identity tracks, IK target at exact chain length. Perf gate on full pipeline evaluation.

---

## V2 — The Quality Layer

**Goal:** Make animation feel good. The difference between V1 and V2 is the difference between technically correct and cinematically convincing.

### Inertial Blending
Replaces fixed-time crossfades with physics-motivated continuation. When a transition fires, instead of blending from the outgoing pose over a fixed duration, the system records the current velocity of each joint and extrapolates a smooth continuation using a critically-damped spring. The result is that transitions feel like the character naturally carries their momentum into the new state rather than sliding between two poses. Developed by David Bollo at Ubisoft, published GDC 2020. Small implementation surface, enormous perceived quality improvement. Works transparently on top of the existing state machine — callers don't change their code.

### Multi-Layer Blend Tree
Activate the `BlendLayer` stack declared in V1. Upper body layer, lower body layer, full-body override layer, additive overlay layer — each with its own `BoneMask` and `LayerMode`. Classic use case: lower body plays locomotion, upper body plays aim or gesture, additive layer plays breathing. Layers evaluated independently and composited in order. Mask blending is per-joint weight (0.0 to 1.0), not binary — allows smooth shoulder transitions between upper and lower body zones.

### Additive Procedural Layers
Small parameterized procedural animations layered additively on any state. Breathing cycle (amplitude driven by exhaustion parameter), weight shift (driven by idle time), eye dart (driven by alert parameter), head turn toward point of interest. Each procedural layer is a `ProceduralNode` implementing `BlendNode` — slots into the existing blend tree naturally. Makes characters feel alive between action states with zero authored animation data.

### Transition Interruption
Allow a transition to be interrupted by a higher-priority condition before it completes. Source state for the new transition becomes the in-progress blend rather than either endpoint. Requires tracking the current blend snapshot, not just the source and destination states. Essential for responsive character control — without it, a running character can't start a jump until the walk→run transition finishes.

### Root Motion Extraction
Extract translation and rotation from the root joint and expose it separately from the pose. The renderer gets a pose with root joint at origin; the game gets a `RootMotionDelta` per frame to drive the character controller. Accumulation across loop boundaries handled correctly. Essential for animation-driven movement — without root motion, locomotion is always sliding.

### Animation Events
Clips annotated with named events at specific normalized times — foot plant, hand contact, weapon swing, sound cue. `AnimatorInstance` fires a callback when playback crosses an event time. Used for footstep sounds, hit detection windows, particle spawning. Events survive blend tree evaluation — if two clips with foot plant events are blended, the event fires at the weighted average time.

### Clip Compression
Optional quantization pass on `TransformTrack` arrays. Rotations quantized to 16-bit per component with smallest-three encoding (drop the largest quaternion component, store sign bit). Translations delta-encoded with configurable step size from `QuantizationSpec`. Scales similarly. Decompression runs in the sampler hot path. Targets 50-60% memory reduction on typical clip libraries with no perceptible quality loss.

### Skeletal Retargeting
Map a clip authored for skeleton A onto skeleton B with different proportions or joint hierarchy. Two-pass: structural mapping (match joints by name or manual override), then proportion adjustment (scale translations by limb length ratios). Handles missing joints (target has no finger bones — skip), extra joints (target has additional spine joints — distribute the motion). Essential for any game with multiple character types sharing animation libraries.

---

## V3 — The Frontier

**Goal:** Features that AAA studios have in their proprietary engines and nobody has shipped in open source at this quality level.

### Motion Matching
Replace the state machine entirely with a database of annotated pose snapshots. Every frame, the runtime searches the database for the pose that best matches the current character state — current pose, velocity, desired future trajectory. The best match becomes the next frame. No blend tree authoring, no transition tuning, no state explosion as gameplay complexity grows. The data model addition is `MotionDatabase` — clips decomposed into frames annotated with joint velocities, foot contacts, and trajectory tags. Search is a nearest-neighbor query over a feature vector; with a good feature set it runs in under 0.5ms on databases of tens of thousands of frames. This is what Ubisoft shipped in For Honor, what Naughty Dog uses for The Last of Us, what every major studio is moving toward.

### Learned Motion Matching / Neural Pose Prediction
Replace or augment the motion database search with a small neural network. The network takes current pose + desired velocity as input and predicts the next pose directly. No database search, no blending — pure inference. Networks in production are tiny (4 fully-connected layers, ~100K parameters) and run in under 1ms per character. Animis ships this as an optional `animis-neural` module with ONNX Runtime as the inference backend. The training pipeline is out of scope (that's a Python/PyTorch problem) but Animis defines the export format and inference interface. This is what Unity's Kinematica research targets and what EA's FIFA team published in 2021.

### Physics-Based Secondary Motion
Bones not driven by keyframe animation react to physics — hair, cape, ears, ponytail, loose equipment, jiggle. Integration with DynamisCollision's constraint solver. Each secondary bone chain has a `SecondaryChainDef` — spring stiffness, damping, angular limits, collision capsules. The constraint solver runs after the primary pose is evaluated and before skinning. Characters feel physical rather than rigid. The DynamisCollision integration contract defined earlier was designed with exactly this in mind — bone transforms feed directly into collision body state.

### Full-Body Physics Character (Euphoria-style)
The north star. Instead of keyframe animation driving the skeleton, a constraint solver drives the character from muscle-like torque actuators at each joint, with the keyframe pose as the *target* rather than the *output*. The character physically reaches toward their animated pose rather than snapping to it. Results: automatic adaptation to uneven terrain, realistic reactions to external forces and hits, natural recovery from perturbations, emergent behavior from physics interactions. Keyframe animation becomes a control signal rather than a final answer. This is what Euphoria (GTA V, Red Dead Redemption 2) does and it is the most impressive animation technology in games. Nobody has shipped this in open source. The architecture is: `AnimatorInstance` produces a target pose, a `PhysicsCharacterController` uses DynamisCollision constraints to drive the skeleton toward that target, the resulting physically-simulated pose feeds into skinning.

### Pose Search and Semantic Tagging
Clips and motion database frames tagged with semantic metadata — foot contacts, hand positions, facing direction, environment interaction points. Runtime queries: "find a pose where left foot is planted," "find a reach pose toward this world position," "find a pose within 30 degrees of target facing." Enables seamless environment interaction — vaulting, mantling, leaning, cover — without manual authoring of every case. The tagging schema is extensible so game-specific semantics (weapon grip type, emotional state, injury level) can be added without touching the library.

### Pose Warping
Stretch and rotate a clip at runtime to fit the environment without re-authoring. A vault animation authored for a 1m obstacle stretches to fit a 1.3m obstacle. A reach animation authored for a straight-ahead grab rotates to reach 30 degrees left. Implemented as a post-evaluation pass that adjusts joint transforms to satisfy geometric constraints while preserving the character of the animation. Dramatically reduces the combinatorial explosion of environmental interaction clips.

### FABRIK Solver and Full IK Stack
Full Forwards And Backwards Reaching Inverse Kinematics for spine chains, tails, tentacles, and any multi-joint chain. Complements the two-bone analytical solver from V1. Additional solvers: aim constraint (rotate a joint to point toward a world target), look-at constraint (head tracking), foot planting (detect ground contact and adjust foot IK to eliminate float). Full IK stack means a character's feet stay planted on uneven terrain, hands reach correct door handles regardless of character height variation, head tracks targets of interest — all automatically.

---

## The Stack When Complete

```
Vectrix          — math foundation
MeshForge        — mesh processing and packing
DynamisCollision — collision detection and response
Animis           — animation
  animis         — data model
  animis-runtime — evaluation pipeline
  animis-neural  — ONNX inference (V3)
```
