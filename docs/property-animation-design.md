# Property Animation Design — Animis Transform Lane

## Architectural Decision

Transform/property animation for non-skeletal objects is a **first-class Animis capability**.
Proving modules and game code must use Animis for animation rather than module-local systems.

## Two-Lane Architecture

Animis has two parallel animation runtime paths:

### Skeletal Lane
- `Clip` → `TransformTrack(jointIndex)` → `ClipSampler` → `Pose`/`PoseBuffer`
- Joint-indexed tracks, quaternion rotations, bind pose fallback
- Skinning output (matrix palette for GPU)
- Designed for rigged character animation

### Property Lane
- `PropertyClip` → `PropertyTrack(name)` → `PropertyPlayer`/`PropertyBlender`
- String-named channels, float values, linear interpolation
- No skeleton, no joints, no skinning
- Designed for: object transforms, material parameters, light intensities, camera FOV, UI transitions

### What they share
- Clip duration semantics
- Looping behavior
- `AnimationEvent` markers (same type, same crossing detection logic)
- Playback controls (play/pause/reset/speed)
- Blend/crossfade behavior
- State-machine integration potential (future)

### What they do NOT share
- Track addressing: joint index (skeletal) vs string name (property)
- Output format: Pose/SkinningOutput (skeletal) vs named float samples (property)
- Data density: skeletal tracks are TRS per joint, property tracks are scalar per channel

## Why not fake skeletons?

Property animation does NOT pretend to be skeletal animation internally.
No fake skeletons, fake joints, or fake poses. The model is fundamentally different:
- Skeletal: fixed joint hierarchy with indexed access
- Property: flat named channels with string-keyed access

Forcing property animation through the skeletal path would create unnecessary complexity
and fragile coupling.

## Track binding semantics

Property tracks are identified by **string name**. The binding convention is:

- Simple names for single-object animation: `"posX"`, `"rotY"`, `"scale"`, `"opacity"`
- Dotted paths for multi-target scenes: `"hero.position.y"`, `"pointLight.intensity"`
- The proving modules use simple names; structured binding is a v2 concern

The binding target is resolved by the consumer (game code or subsystem adapter),
not by Animis itself. Animis samples the value; the consumer applies it.

## PropertyClip naming rationale

`PropertyClip` was chosen over `TransformClip` because the capability is broader
than object transforms. The same system should later drive:
- Material parameter animation
- Light intensity/color animation
- Camera FOV/path animation
- UI transition curves
- Any time-driven float parameter

## V1 scope (current)

### Data model (`animis/transform`)
- `PropertyKeyframe(time, value)` — scalar keyframe
- `PropertyTrack(name, keyframes)` — named float channel with linear interpolation
- `PropertyClip(name, duration, tracks, events)` — collection of tracks + event markers

### Runtime (`animis-runtime/transform`)
- `PropertyPlayer` — playback controller with event crossing detection
- `PropertyBlender` — crossfade between two PropertyClips
- `PropertySampleResult` — fired events per frame

## V2 roadmap (planned, not yet implemented)

### Richer track types
- `Vec3PropertyTrack` — 3-component vector channel (position, color)
- `QuatPropertyTrack` — quaternion channel with slerp interpolation (rotation)
- `IntPropertyTrack` — integer channel (frame indices, state flags)

### Interpolation modes
- Linear (current)
- Hermite/Bezier spline
- Step (hold previous value until next keyframe)

### State machine integration
- `PropertyStateMachine` — state machine driving PropertyClips
- Condition-based transitions (same `ConditionExpr` as skeletal)

### Additive layering
- Additive property clips (offset values composed onto base)

### Structured binding
- Path-based target resolution: `"entity.component.property"`
- Binding registry for type-safe target lookup

## Doctrine

1. **Property animation is canonical Animis** — no module-local animation systems
2. **Two lanes, one mental model** — skeletal and property share concepts, not implementations
3. **Consumer resolves binding** — Animis samples values, consumers apply them
4. **Float-first, expand later** — v1 is scalar; vec3/quat are planned, not premature
5. **Events are shared** — `AnimationEvent` is the same type in both lanes
