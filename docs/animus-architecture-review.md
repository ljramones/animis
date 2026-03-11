# Animus Architecture Boundary Ratification Review

Date: 2026-03-11

## 1. Intent and Scope

Animus should be an animation evaluation subsystem: it should evaluate animation state, generate/blend poses, sample clips, run animation graph/state-machine logic, support retargeting and skeletal mapping, emit animation events, and report root-motion deltas.

Animus should own:
- animation state evaluation
- pose generation
- pose blending
- clip playback/sampling
- animation graph/state-machine evaluation
- retargeting and clip adaptation
- animation event production
- root-motion extraction/reporting

Animus should not own:
- authoritative transform truth for world entities
- gameplay state authority
- simulation execution authority
- world mutation authority
- global orchestration authority

## 2. Repo Overview (Grounded)

Repository shape (`pom.xml`) is a 6-module Maven build:
- `animis` (model definitions)
- `animis-runtime` (evaluation/runtime)
- `animis-loader` (gltf/bvh loading)
- `animis-neural` (onnx inference)
- `animis-perf` (benchmarks)
- `animis-demo` (integration/demo)

Major packages and APIs:
- `org.animis.*` model module: skeleton, clips, blend nodes, state-machine definitions, IK chain definitions, retarget maps, motion database/tagging.
- `org.animis.runtime.api`: `AnimationRuntime`, `AnimatorInstance`, `RootMotionDelta`, motion matching/search and retarget/compression interfaces.
- `org.animis.runtime.*` runtime internals surfaced publicly: blend evaluators, sampling, IK solvers, skinning, pose warping, secondary motion, and physics character controller.
- `org.animis.loader`: `AnimationLoader`, `AnimationLoaderFactory`, `GltfAnimationLoader`, `BvhAnimationLoader`.
- `org.animis.neural.api`: `NeuralPosePredictor`, `OnnxNeuralPosePredictor`, prediction/model-info records.

Notable abstractions and runtime surfaces:
- Runtime updates via `AnimatorInstance.update(deltaSeconds)` pipeline (state machine -> blend -> IK -> warp -> secondary -> optional physics -> skinning).
- Root motion is reported as `RootMotionDelta` output, not automatically applied to world transforms.
- Events are accumulated by sampler/blend and then dispatched through per-event listeners in `DefaultAnimatorInstance`.
- Optional physics-character support in runtime uses `DefaultPhysicsCharacterController` with `collision_detection` types.

## 3. Strict Ownership Statement

Animus should exclusively own:
- animation clip sampling and interpolation
- animation graph/state-machine evaluation
- blend tree/layer composition and inertial blending
- per-character pose evaluation buffers and immutable pose snapshots
- IK solving and pose warping as animation-stage operations
- secondary motion as animation-local pose refinement
- retargeting/compression/motion-search utilities
- animation event production and root-motion delta extraction/reporting
- skinning palette generation for render consumers

## 4. Explicit Non-Ownership

Animus must not own:
- authoritative world transform state
- world lifecycle/runtime orchestration
- ECS storage/ownership
- scene graph ownership
- physics subsystem authority for global simulation
- collision subsystem authority
- gameplay rule/state authority
- AI decision authority
- rendering/GPU execution authority
- persistence/session authority
- input authority

## 5. Dependency Rules

Allowed dependencies for Animus:
- content/asset model definitions (clips, skeletons, state-machine data)
- mesh/asset loading utilities for animation asset ingestion (`meshforge-loader`)
- math/linear algebra utilities for evaluation (`vectrix`)
- event-emission interfaces and callback hooks for animation notifications
- root-motion and pose output contracts consumed by external execution layers
- optional ML inference backend for animation prediction (`onnxruntime` in `animis-neural`)

Forbidden dependency patterns for Animus:
- direct authoritative world transform commits as source of truth
- direct world orchestration ownership
- direct ECS/SceneGraph ownership
- direct gameplay-state rule execution ownership
- rendering/GPU execution ownership
- unbounded simulation authority that bypasses Physics subsystem contracts

Repo-grounded dependency observations:
- No direct dependencies on WorldEngine, ECS, SceneGraph, DynamisAI, Scripting, LightEngine, or GPU modules were found.
- `animis-runtime` directly depends on `collision_detection` and performs simulation stepping in `DefaultPhysicsCharacterController`.

## 6. Public vs Internal Boundary Assessment

API/internal split is only partially clean.

Findings:
- Good top-level API exists in `org.animis.runtime.api` (`AnimationRuntime`, `AnimatorInstance`, etc.).
- `module-info.java` for `org.animis.runtime` exports many non-API implementation packages (`pose`, `ik`, `physics`, `secondary`, `warp`, `skinning`), making internal implementation details publicly consumable.
- Default implementation classes (`DefaultAnimatorInstance`, `DefaultAnimationRuntime`, `DefaultPhysicsCharacterController`) are in exported packages and effectively part of public surface.
- There is an internal marker package (`org.animis.runtime.internal`), but most implementation remains in exported packages.

Assessment: public/internal boundaries are functional but broad; implementation leakage risk is real.

## 7. Authority Leakage or Overlap

Overall boundary is mostly animation-centric, but overlap exists in two areas.

Confirmed clean separations:
- No direct ownership of WorldEngine, ECS, SceneGraph, rendering, or GPU execution found.
- Root motion is emitted as delta (`RootMotionDelta`) rather than auto-committed to world transforms.
- Runtime outputs are pose/skinning/event surfaces and remain character-local by design.

Overlap/leakage findings:
- Physics/Collision overlap:
  - `DefaultPhysicsCharacterController` creates and advances a `PhysicsStep3D` and applies solver results into pose data.
  - This is direct simulation stepping inside Animus, which pressures the boundary with Physics authority.
- Animation event execution boundary pressure:
  - `AnimatorInstance` exposes `setEventListener(String, Runnable)` and `DefaultAnimatorInstance.update()` calls `listener.run()` directly.
  - This can execute arbitrary gameplay side effects inside animation tick instead of strictly emitting advisory events.

Distinction:
- Animus is correctly strong at animation output generation.
- Animus should remain output/advisory-oriented and avoid becoming an execution authority for non-animation domain behavior.

## 8. Relationship Clarification

- WorldEngine:
  - Animus should consume per-entity animation inputs/parameters.
  - Animus should emit pose/root-motion/event outputs.
  - Animus should not own world lifecycle or authoritative transform commit.

- ECS:
  - Animus should consume ECS-provided animation parameters/rig references via adapters.
  - Animus should emit animation outputs for ECS/world execution layers.
  - Animus should not own ECS state.

- SceneGraph:
  - Animus should consume scene/rig transforms as inputs when needed.
  - Animus should not own scene hierarchy or transform truth.

- Physics:
  - Animus should request or consume bounded physics services for animation-local physicalization.
  - Animus should not be the canonical physics stepping authority for game simulation.

- Collision:
  - Animus may consume collision query/solver primitives for animation-local constraints.
  - Animus should not own collision substrate lifecycle.

- DynamisAI:
  - Animus should consume AI intent signals (gesture/emote/action cues).
  - Animus should emit animation events/pose results.
  - Animus should not assume AI decision authority.

- Scripting:
  - Animus should emit advisory animation events through bounded interfaces.
  - Animus should not execute scripting/gameplay logic directly.

- Event:
  - Animus should publish animation events and consume animation commands.
  - Animus should not own event routing infrastructure.

- Content:
  - Animus should consume skeleton/clip/state machine/retarget data assets.
  - Animus should not own global content pipeline authority.

- LightEngine/render systems:
  - Animus should emit skinning matrices/pose outputs for render consumption.
  - Animus should not own render planning or GPU execution.

## 9. Ratification Result

**Boundary ratified with minor tightening recommended**.

Justification:
- The implemented core is clearly animation-evaluation focused (sampling, blending, state machines, IK, warping, skinning, root-motion extraction).
- No direct world/ECS/scene/render authority was found.
- Tightening is recommended because runtime currently includes direct collision-based simulation stepping and direct `Runnable` event execution hooks, both of which can blur subsystem authority if left unconstrained.

## 10. Boundary Rules Going Forward

- Animus must generate animation state and pose outputs, not own authoritative world-state mutation.
- Root-motion must remain reported output; application to world transforms must be executed by external authority.
- Animation events must remain advisory by default; execution of gameplay side effects should be mediated by external event/execution layers.
- Animus must not become a hidden gameplay or orchestration authority.
- Any animation-local physics must stay scoped to pose refinement and must not replace Physics subsystem authority for world simulation.
- Animus runtime public API should be narrowed so core contracts are stable and implementation packages remain internal.
- Animus must continue avoiding direct rendering/GPU ownership and only provide render-consumable skinning outputs.
