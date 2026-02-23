# Animis

**Animis** is a pure-Java skeletal animation library for real-time engines — from correct clip sampling to motion matching, neural pose prediction, and physics-driven characters. Vectrix-powered math, MeshForge mesh integration, DynamisCollision-ready bone shapes. No native dependencies.

## What it does

Animis evaluates skeletal animation at every layer of sophistication — from simple clip playback to the kind of animation technology that makes AAA studios say *how did they do that*.

The library is organized as a strict model/evaluation split:

- **`animis`** — immutable data model: skeletons, clips, blend trees, state machines, IK chains. Tools and asset pipelines depend on this only.
- **`animis-runtime`** — clip sampling, blend tree evaluation, state machines, two-bone IK, skinning output. Depends on Vectrix and MeshForge.
- **`animis-neural`** — ONNX-backed neural pose prediction for learned motion matching.
- **`animis-demo`** — integration examples and visual debugging.

## Capability roadmap

**V1 — The correct foundation**
Clip sampling with bind-pose fallback, blend trees (lerp, 1D, additive), state machines with cross-fade transitions, two-bone analytical IK with pole targets and stretch, linear blend skinning output.

**V2 — The quality layer**
Inertial blending, multi-layer blend trees with bone masks, additive procedural layers (breathing, weight shift), transition interruption, root motion extraction, animation events, clip compression, skeletal retargeting.

**V3 — The frontier**
Motion matching, learned motion matching via ONNX, physics-based secondary motion via DynamisCollision, full-body physics character (Euphoria-style), pose search and semantic tagging, pose warping, full IK stack with FABRIK.

## Requirements

- JDK 25
- Maven 3.9+
- Local Maven artifacts: `org.vectrix:vectrix:1.10.9`, `org.meshforge:meshforge:1.1.0`

## Build

```bash
mvn clean verify
```

## Stack

```
Vectrix          — math
MeshForge        — mesh processing
DynamisCollision — collision
Animis           — animation
```
