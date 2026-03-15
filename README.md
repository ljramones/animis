# Animis

**Animis** is a pure-Java skeletal animation library for real-time engines — from correct clip
sampling to motion matching, neural pose prediction, and physics-driven characters.
Vectrix-powered math, MeshForge mesh integration, DynamisCollision-ready bone shapes.
No native dependencies.

## What it does

Animis evaluates skeletal animation at every layer of sophistication — from simple clip playback
to the kind of animation technology that makes AAA studios say *how did they do that*.

The library is organized as a strict model/evaluation split with JPMS-enforced boundaries:

| Module | Role |
|---|---|
| `animis` | Immutable data model: skeletons, clips, blend trees, state machines, IK chains. No runtime state, no dependencies. Tools and asset pipelines depend on this only. |
| `animis-runtime` | Full evaluation pipeline: clip sampling, blend trees, state machines, IK, skinning, secondary motion, physics character. Depends on Vectrix and MeshForge. |
| `animis-loader` | Format importers: glTF (JSON) and BVH. Depends on animis and meshforge-loader. Does not depend on animis-runtime. |
| `animis-neural` | ONNX-backed neural pose prediction for learned motion matching. |
| `animis-perf` | JMH benchmarks and perf gate. |
| `animis-demo` | Integration examples and visual debugging. |

## Capabilities

### V1 — The correct foundation
Clip sampling with bind-pose fallback for untracked joints, loop wrap-around, and on-the-fly
decompression. Blend trees covering lerp, 1D blend spaces, additive overlays, and procedural
nodes. State machines with condition-based transitions. Two-bone analytical IK with pole targets
and stretch clamping. Linear blend skinning output as a GPU-ready matrix palette.

### V2 — The quality layer
Inertial blending on transitions — joints carry momentum rather than sliding between poses.
Multi-layer blend trees with per-joint float-weighted bone masks. Additive procedural layers:
breathing, weight shift, head tracking. Transition interruption with inertial re-capture.
Root motion extraction with correct loop boundary handling. Animation events with per-name
listener dispatch. Clip compression using smallest-three quaternion encoding and delta-encoded
translations. Skeletal retargeting with joint name mapping and limb proportion scaling.

### V3 — The frontier
Motion matching — nearest-neighbor search over a MotionDatabase of annotated pose frames,
weighted by pose features, trajectory, and contact flags. Learned motion matching via ONNX
Runtime — a small neural network predicts the next pose directly from current state and desired
velocity. Physics-based secondary motion — hair, cape, and loose equipment driven by
spring-damping chains. Pose search and semantic tagging — query clips by foot contact state,
hand position, or any custom tag. Pose warping — stretch and rotate clips at runtime to fit
environment geometry. Full IK stack: FABRIK solver for spine chains and arbitrary-length
multi-joint targets. Full-body physics character — joints driven by DynamisCollision spring
torques toward a keyframe target, reacting physically to external forces (Euphoria-style).

## Performance

All hot paths are allocation-free in steady state. PoseBuffer is reused across frames.
Thread-local scratch buffers eliminate allocation in the blend tree evaluator. Benchmarks
run under forked JMH on JDK 25 (canonical run: `perf/comprehensive-20260223-095130.csv`):

| Operation | Result | Target |
|---|---|---|
| `AnimatorInstance.update()` — 100 joints | 0.009 ms/op | ≤ 1.000 ms |
| `MotionMatcher.findBest()` — 10K frames | 0.213 ms/op | ≤ 0.500 ms |
| `ClipSampler.sample()` — 60s clip | 0.001 ms/op | ≤ 0.300 ms |
| `SkinningComputer.compute()` — 100 joints | 0.007 ms/op | ≤ 0.300 ms |

Run the perf gate:

```bash
mvn -f animis-perf/pom.xml -DskipTests compile exec:java -Dexec.mainClass=org.dynamisengine.animis.perf.BenchmarkRunner
./perf/check_baseline.sh
```

## Requirements

- JDK 25
- Maven 3.9+
- Local Maven artifacts:
    - `org.vectrix:vectrix:1.10.9`
    - `org.meshforge:meshforge:1.1.0`
    - `org.dynamiscollision:collision_detection:1.1.0`

## Build and test

```bash
mvn clean verify
```

Run tests only:

```bash
mvn -q test
```

## Documentation 

- [technology_explainer.md](docs/technology_explainer.md) — deep dive into every pipeline stage,
  algorithm, and design decision
- [docs/roadmap.md](docs/roadmap.md) — known gaps and future work

## Stack

```
Vectrix          — SIMD-optimized math: vectors, matrices, quaternions, noise, color science
MeshForge        — mesh processing: loading, ops, packing, morph targets, skinned mesh
DynamisCollision — collision detection: broadphase, GJK/EPA, contact solving, CCD
Animis           — skeletal animation: V1 through V3
```

No library in this stack depends on any layer above it.
