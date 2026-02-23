package dev.ljramones.animis.runtime.sampling;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.TrackMetadata;
import dev.ljramones.animis.clip.TransformTrack;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;

public final class DefaultClipSampler implements ClipSampler {
  @Override
  public void sample(
      final Clip clip,
      final Skeleton skeleton,
      final float timeSeconds,
      final boolean loop,
      final PoseBuffer outPose) {
    if (outPose.jointCount() < skeleton.joints().size()) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }
    applyBindPose(skeleton, outPose);
    if (clip.tracks().isEmpty()) {
      return;
    }

    final float normalizedTime = normalizeTime(timeSeconds, clip.durationSeconds(), loop);
    for (final TransformTrack track : clip.tracks()) {
      sampleTrack(track, normalizedTime, loop, outPose, skeleton.joints().size());
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
      final boolean loop,
      final PoseBuffer outPose,
      final int skeletonJointCount) {
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
    final float tx = lerp(read3(track.translations(), i0, 0, 0f), read3(track.translations(), i1, 0, 0f), alpha);
    final float ty = lerp(read3(track.translations(), i0, 1, 0f), read3(track.translations(), i1, 1, 0f), alpha);
    final float tz = lerp(read3(track.translations(), i0, 2, 0f), read3(track.translations(), i1, 2, 0f), alpha);
    outPose.setTranslation(joint, tx, ty, tz);

    final float sx = lerp(read3(track.scales(), i0, 0, 1f), read3(track.scales(), i1, 0, 1f), alpha);
    final float sy = lerp(read3(track.scales(), i0, 1, 1f), read3(track.scales(), i1, 1, 1f), alpha);
    final float sz = lerp(read3(track.scales(), i0, 2, 1f), read3(track.scales(), i1, 2, 1f), alpha);
    outPose.setScale(joint, sx, sy, sz);

    final float[] q = slerp(
        read4(track.rotations(), i0, 0),
        read4(track.rotations(), i0, 1),
        read4(track.rotations(), i0, 2),
        read4(track.rotations(), i0, 3),
        read4(track.rotations(), i1, 0),
        read4(track.rotations(), i1, 1),
        read4(track.rotations(), i1, 2),
        read4(track.rotations(), i1, 3),
        clamp(alpha, 0f, 1f));
    outPose.setRotation(joint, q[0], q[1], q[2], q[3]);
  }

  private static int sampleCount(final TransformTrack track) {
    final TrackMetadata metadata = track.metadata();
    if (metadata.sampleCount() > 0) {
      return metadata.sampleCount();
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
}
