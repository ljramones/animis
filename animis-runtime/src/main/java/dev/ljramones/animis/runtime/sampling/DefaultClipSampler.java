package dev.ljramones.animis.runtime.sampling;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.AnimationEvent;
import dev.ljramones.animis.clip.RootMotionDef;
import dev.ljramones.animis.clip.TrackMetadata;
import dev.ljramones.animis.clip.TransformTrack;
import dev.ljramones.animis.clip.CompressedTrackData;
import dev.ljramones.animis.runtime.api.RootMotionDelta;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;

public final class DefaultClipSampler implements ClipSampler {
  @Override
  public ClipSampleResult sample(
      final Clip clip,
      final Skeleton skeleton,
      final float timeSeconds,
      final float previousTimeSeconds,
      final boolean loop,
      final PoseBuffer outPose) {
    if (outPose.jointCount() < skeleton.joints().size()) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }
    applyBindPose(skeleton, outPose);
    if (clip.tracks().isEmpty()) {
      return new ClipSampleResult(RootMotionDelta.ZERO, detectEvents(clip, previousTimeSeconds, timeSeconds, loop));
    }

    final float normalizedTime = normalizeTime(timeSeconds, clip.durationSeconds(), loop);
    final float normalizedPrevTime = normalizeTime(previousTimeSeconds, clip.durationSeconds(), loop);
    RootMotionDelta rootMotionDelta = RootMotionDelta.ZERO;
    final TransformTrack rootTrack = clip.rootMotion()
        .map(def -> findTrack(clip, def.rootJoint()))
        .orElse(null);
    if (rootTrack != null && clip.rootMotion().isPresent()) {
      rootMotionDelta = computeRootMotionDelta(
          rootTrack,
          clip.rootMotion().get(),
          timeSeconds,
          previousTimeSeconds,
          clip.durationSeconds(),
          loop);
    }

    for (final TransformTrack track : clip.tracks()) {
      sampleTrack(
          track,
          normalizedTime,
          normalizedPrevTime,
          loop,
          outPose,
          skeleton.joints().size(),
          clip.rootMotion().orElse(null));
    }
    return new ClipSampleResult(rootMotionDelta, detectEvents(clip, previousTimeSeconds, timeSeconds, loop));
  }

  private static java.util.List<String> detectEvents(
      final Clip clip,
      final float previousTimeSeconds,
      final float currentTimeSeconds,
      final boolean loop) {
    if (clip.events().isEmpty() || clip.durationSeconds() <= 0f) {
      return java.util.List.of();
    }
    final float duration = clip.durationSeconds();
    final float prev = normalizeTime(previousTimeSeconds, duration, loop);
    final float curr = normalizeTime(currentTimeSeconds, duration, loop);
    final java.util.ArrayList<String> fired = new java.util.ArrayList<>();
    if (!loop || curr >= prev) {
      fireEventsInRange(clip.events(), prev, curr, duration, fired);
    } else {
      fireEventsInRange(clip.events(), prev, duration, duration, fired);
      fireEventsInRange(clip.events(), 0f, curr, duration, fired);
    }
    return fired;
  }

  private static void fireEventsInRange(
      final java.util.List<AnimationEvent> events,
      final float start,
      final float end,
      final float duration,
      final java.util.List<String> out) {
    for (final AnimationEvent event : events) {
      final float t = clamp(event.normalizedTime(), 0f, 1f) * duration;
      if (t > start && t <= end) {
        out.add(event.name());
      }
    }
  }

  private static void applyBindPose(final Skeleton skeleton, final PoseBuffer outPose) {
    for (final Joint joint : skeleton.joints()) {
      final BindTransform bind = joint.bind();
      outPose.setTranslation(joint.index(), bind.tx(), bind.ty(), bind.tz());
      outPose.setRotation(joint.index(), bind.qx(), bind.qy(), bind.qz(), bind.qw());
      outPose.setScale(joint.index(), bind.sx(), bind.sy(), bind.sz());
    }
  }

  private static float normalizeTime(final float timeSeconds, final float durationSeconds, final boolean loop) {
    if (durationSeconds <= 0f) {
      return 0f;
    }
    if (!loop) {
      return clamp(timeSeconds, 0f, durationSeconds);
    }
    final float wrapped = timeSeconds % durationSeconds;
    return wrapped < 0f ? wrapped + durationSeconds : wrapped;
  }

  private static void sampleTrack(
      final TransformTrack track,
      final float timeSeconds,
      final float previousTimeSeconds,
      final boolean loop,
      final PoseBuffer outPose,
      final int skeletonJointCount,
      final RootMotionDef rootMotionDef) {
    if (track.jointIndex() < 0 || track.jointIndex() >= skeletonJointCount) {
      throw new IllegalArgumentException("Track jointIndex is out of bounds for skeleton");
    }
    final int sampleCount = sampleCount(track);
    if (sampleCount <= 0) {
      return;
    }

    final float interval = sampleInterval(track.metadata(), sampleCount);
    final int i0;
    final int i1;
    final float alpha;

    if (sampleCount == 1 || interval <= 0f) {
      i0 = 0;
      i1 = 0;
      alpha = 0f;
    } else {
      final float frame = timeSeconds / interval;
      final int base = (int) Math.floor(frame);
      final float frac = frame - base;
      if (loop) {
        i0 = floorMod(base, sampleCount);
        i1 = (i0 + 1) % sampleCount;
        alpha = frac;
      } else {
        i0 = clamp(base, 0, sampleCount - 1);
        i1 = i0 >= sampleCount - 1 ? i0 : i0 + 1;
        alpha = i0 == i1 ? 0f : clamp(frac, 0f, 1f);
      }
    }

    final int joint = track.jointIndex();
    float tx = lerp(readTranslation(track, i0, 0), readTranslation(track, i1, 0), alpha);
    float ty = lerp(readTranslation(track, i0, 1), readTranslation(track, i1, 1), alpha);
    float tz = lerp(readTranslation(track, i0, 2), readTranslation(track, i1, 2), alpha);
    if (rootMotionDef != null && track.jointIndex() == rootMotionDef.rootJoint()) {
      if (rootMotionDef.extractTranslationXZ()) {
        tx = 0f;
        tz = 0f;
      }
      if (rootMotionDef.extractTranslationY()) {
        ty = 0f;
      }
    }
    outPose.setTranslation(joint, tx, ty, tz);

    final float sx = lerp(readScale(track, i0, 0), readScale(track, i1, 0), alpha);
    final float sy = lerp(readScale(track, i0, 1), readScale(track, i1, 1), alpha);
    final float sz = lerp(readScale(track, i0, 2), readScale(track, i1, 2), alpha);
    outPose.setScale(joint, sx, sy, sz);

    final float[] q0 = readRotation(track, i0);
    final float[] q1 = readRotation(track, i1);
    float[] q = slerp(
        q0[0], q0[1], q0[2], q0[3],
        q1[0], q1[1], q1[2], q1[3],
        clamp(alpha, 0f, 1f));
    if (rootMotionDef != null && track.jointIndex() == rootMotionDef.rootJoint() && rootMotionDef.extractRotationY()) {
      q = removeYaw(q);
    }
    outPose.setRotation(joint, q[0], q[1], q[2], q[3]);
  }

  private static TransformTrack findTrack(final Clip clip, final int jointIndex) {
    for (final TransformTrack track : clip.tracks()) {
      if (track.jointIndex() == jointIndex) {
        return track;
      }
    }
    return null;
  }

  private static RootMotionDelta computeRootMotionDelta(
      final TransformTrack rootTrack,
      final RootMotionDef rootMotionDef,
      final float timeSeconds,
      final float previousTimeSeconds,
      final float durationSeconds,
      final boolean loop) {
    if (durationSeconds <= 0f) {
      return RootMotionDelta.ZERO;
    }
    final RootSample current = sampleRootUnwrapped(rootTrack, timeSeconds, durationSeconds, loop);
    final RootSample previous = sampleRootUnwrapped(rootTrack, previousTimeSeconds, durationSeconds, loop);
    float dx = current.tx - previous.tx;
    float dy = current.ty - previous.ty;
    float dz = current.tz - previous.tz;
    float dyaw = normalizeAngle(current.yaw - previous.yaw);
    if (!rootMotionDef.extractTranslationXZ()) {
      dx = 0f;
      dz = 0f;
    }
    if (!rootMotionDef.extractTranslationY()) {
      dy = 0f;
    }
    if (!rootMotionDef.extractRotationY()) {
      dyaw = 0f;
    }
    return new RootMotionDelta(dx, dy, dz, dyaw);
  }

  private static RootSample sampleRootUnwrapped(
      final TransformTrack track,
      final float absoluteTime,
      final float duration,
      final boolean loop) {
    if (!loop) {
      return sampleRootAt(track, clamp(absoluteTime, 0f, duration), duration);
    }
    final int loops = (int) Math.floor(absoluteTime / duration);
    final float normalized = normalizeTime(absoluteTime, duration, true);
    final RootSample inCycle = sampleRootAt(track, normalized, duration);
    final RootSample start = sampleRootAt(track, 0f, duration);
    final RootSample end = sampleRootAt(track, duration, duration);
    final float cycleDx = end.tx - start.tx;
    final float cycleDy = end.ty - start.ty;
    final float cycleDz = end.tz - start.tz;
    final float cycleDyaw = normalizeAngle(end.yaw - start.yaw);
    return new RootSample(
        inCycle.tx + loops * cycleDx,
        inCycle.ty + loops * cycleDy,
        inCycle.tz + loops * cycleDz,
        inCycle.yaw + loops * cycleDyaw);
  }

  private static RootSample sampleRootAt(final TransformTrack track, final float timeSeconds, final float duration) {
    final int sampleCount = sampleCount(track);
    if (sampleCount <= 0) {
      return new RootSample(0f, 0f, 0f, 0f);
    }
    final float interval = sampleInterval(track.metadata(), sampleCount);
    final float effectiveInterval = interval > 0f ? interval : (sampleCount > 1 ? duration / (sampleCount - 1f) : 0f);
    final int i0;
    final int i1;
    final float alpha;
    if (sampleCount == 1 || effectiveInterval <= 0f) {
      i0 = 0;
      i1 = 0;
      alpha = 0f;
    } else {
      final float frame = timeSeconds / effectiveInterval;
      final int base = clamp((int) Math.floor(frame), 0, sampleCount - 1);
      i0 = base;
      i1 = base >= sampleCount - 1 ? base : base + 1;
      alpha = i0 == i1 ? 0f : clamp(frame - base, 0f, 1f);
    }
    final float tx = lerp(readTranslation(track, i0, 0), readTranslation(track, i1, 0), alpha);
    final float ty = lerp(readTranslation(track, i0, 1), readTranslation(track, i1, 1), alpha);
    final float tz = lerp(readTranslation(track, i0, 2), readTranslation(track, i1, 2), alpha);
    final float[] q0 = readRotation(track, i0);
    final float[] q1 = readRotation(track, i1);
    final float[] q = slerp(
        q0[0], q0[1], q0[2], q0[3],
        q1[0], q1[1], q1[2], q1[3],
        alpha);
    return new RootSample(tx, ty, tz, yawFromQuat(q[0], q[1], q[2], q[3]));
  }

  private static int sampleCount(final TransformTrack track) {
    final TrackMetadata metadata = track.metadata();
    if (metadata.sampleCount() > 0) {
      return metadata.sampleCount();
    }
    if (track.compressed().isPresent()) {
      return track.compressed().get().rotationMeta().length;
    }
    final int t = track.translations().length / 3;
    final int r = track.rotations().length / 4;
    final int s = track.scales().length / 3;
    return Math.min(t, Math.min(r, s));
  }

  private static float sampleInterval(final TrackMetadata metadata, final int sampleCount) {
    if (metadata.sampleIntervalSeconds() > 0f) {
      return metadata.sampleIntervalSeconds();
    }
    if (metadata.sourceFps() > 0f) {
      return 1f / metadata.sourceFps();
    }
    return sampleCount > 1 ? 1f : 0f;
  }

  private static float lerp(final float a, final float b, final float t) {
    return a + (b - a) * t;
  }

  private static float[] slerp(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bxIn,
      final float byIn,
      final float bzIn,
      final float bwIn,
      final float t) {
    float bx = bxIn;
    float by = byIn;
    float bz = bzIn;
    float bw = bwIn;

    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0f) {
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
      dot = -dot;
    }

    if (dot > 0.9995f) {
      return normalize(
          lerp(ax, bx, t),
          lerp(ay, by, t),
          lerp(az, bz, t),
          lerp(aw, bw, t));
    }

    final float theta0 = (float) Math.acos(dot);
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);

    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalize(
        s0 * ax + s1 * bx,
        s0 * ay + s1 * by,
        s0 * az + s1 * bz,
        s0 * aw + s1 * bw);
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 0f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float invLen = 1f / (float) Math.sqrt(lenSq);
    return new float[] {x * invLen, y * invLen, z * invLen, w * invLen};
  }

  private static float[] removeYaw(final float[] q) {
    final float yaw = yawFromQuat(q[0], q[1], q[2], q[3]);
    final float[] yawQuat = quatFromYaw(yaw);
    final float[] invYaw = new float[] {-yawQuat[0], -yawQuat[1], -yawQuat[2], yawQuat[3]};
    final float[] out = mul(invYaw, q);
    return normalize(out[0], out[1], out[2], out[3]);
  }

  private static float yawFromQuat(final float x, final float y, final float z, final float w) {
    final float siny = 2f * (w * y + x * z);
    final float cosy = 1f - 2f * (y * y + x * x);
    return (float) Math.atan2(siny, cosy);
  }

  private static float[] quatFromYaw(final float yaw) {
    final float half = yaw * 0.5f;
    return new float[] {0f, (float) Math.sin(half), 0f, (float) Math.cos(half)};
  }

  private static float[] mul(final float[] a, final float[] b) {
    return new float[] {
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    };
  }

  private static float normalizeAngle(final float angle) {
    float a = angle;
    while (a > Math.PI) {
      a -= (float) (2.0 * Math.PI);
    }
    while (a < -Math.PI) {
      a += (float) (2.0 * Math.PI);
    }
    return a;
  }

  private static int floorMod(final int a, final int b) {
    final int mod = a % b;
    return mod < 0 ? mod + b : mod;
  }

  private static int clamp(final int value, final int min, final int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static float read3(
      final float[] data,
      final int sampleIndex,
      final int axis,
      final float defaultValue) {
    final int idx = sampleIndex * 3 + axis;
    return idx < data.length ? data[idx] : defaultValue;
  }

  private static float read4(final float[] data, final int sampleIndex, final int axis) {
    final int idx = sampleIndex * 4 + axis;
    if (idx < data.length) {
      return data[idx];
    }
    return axis == 3 ? 1f : 0f;
  }

  private static float readTranslation(final TransformTrack track, final int sampleIndex, final int axis) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      final float step = track.metadata().quantization().posStep() > 0f ? track.metadata().quantization().posStep() : 1e-4f;
      final int idx = sampleIndex * 3 + axis;
      final short q = idx < c.translationDeltas().length ? c.translationDeltas()[idx] : 0;
      final float base = axis == 0 ? c.baseTx() : axis == 1 ? c.baseTy() : c.baseTz();
      return base + q * step;
    }
    return read3(track.translations(), sampleIndex, axis, 0f);
  }

  private static float readScale(final TransformTrack track, final int sampleIndex, final int axis) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      final float step = track.metadata().quantization().scaleStep() > 0f ? track.metadata().quantization().scaleStep() : 1e-4f;
      final int idx = sampleIndex * 3 + axis;
      final short q = idx < c.scaleDeltas().length ? c.scaleDeltas()[idx] : 0;
      final float base = axis == 0 ? c.baseSx() : axis == 1 ? c.baseSy() : c.baseSz();
      return base + q * step;
    }
    return read3(track.scales(), sampleIndex, axis, 1f);
  }

  private static float[] readRotation(final TransformTrack track, final int sampleIndex) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      if (sampleIndex < 0 || sampleIndex >= c.rotationMeta().length) {
        return new float[] {0f, 0f, 0f, 1f};
      }
      final int meta = c.rotationMeta()[sampleIndex] & 0xFF;
      final int largest = meta & 0x3;
      final int sign = ((meta >> 2) & 0x1) == 1 ? 1 : -1;
      final int rb = sampleIndex * 3;
      final float[] kept = new float[] {
          dequantizeUnit(c.rotationSmallestThree()[rb]),
          dequantizeUnit(c.rotationSmallestThree()[rb + 1]),
          dequantizeUnit(c.rotationSmallestThree()[rb + 2])
      };
      final float[] q = new float[4];
      int k = 0;
      float sumSq = 0f;
      for (int i = 0; i < 4; i++) {
        if (i == largest) {
          continue;
        }
        q[i] = kept[k++];
        sumSq += q[i] * q[i];
      }
      q[largest] = sign * (float) Math.sqrt(Math.max(0f, 1f - sumSq));
      return normalize(q[0], q[1], q[2], q[3]);
    }
    return new float[] {
        read4(track.rotations(), sampleIndex, 0),
        read4(track.rotations(), sampleIndex, 1),
        read4(track.rotations(), sampleIndex, 2),
        read4(track.rotations(), sampleIndex, 3)
    };
  }

  private static float dequantizeUnit(final short q) {
    return Math.max(-1f, Math.min(1f, q / 32767f));
  }

  private record RootSample(float tx, float ty, float tz, float yaw) {}
}
