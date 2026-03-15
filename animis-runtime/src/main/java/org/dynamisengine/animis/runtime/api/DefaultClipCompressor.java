package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.CompressedTrackData;
import org.dynamisengine.animis.clip.QuantizationSpec;
import org.dynamisengine.animis.clip.TransformTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultClipCompressor implements ClipCompressor {
  @Override
  public Clip compress(final Clip clip) {
    final List<TransformTrack> outTracks = new ArrayList<>(clip.tracks().size());
    for (final TransformTrack track : clip.tracks()) {
      outTracks.add(compressTrack(track));
    }
    return new Clip(
        clip.id(),
        clip.name(),
        clip.durationSeconds(),
        outTracks,
        clip.rootMotion(),
        clip.events());
  }

  private static TransformTrack compressTrack(final TransformTrack track) {
    final QuantizationSpec q = track.metadata().quantization();
    if (q == null || !q.enabled()) {
      return track;
    }
    final int sampleCount = sampleCount(track);
    if (sampleCount <= 0) {
      return track;
    }

    final float posStep = q.posStep() > 0f ? q.posStep() : 1e-4f;
    final float scaleStep = q.scaleStep() > 0f ? q.scaleStep() : 1e-4f;

    final float baseTx = read3(track.translations(), 0, 0, 0f);
    final float baseTy = read3(track.translations(), 0, 1, 0f);
    final float baseTz = read3(track.translations(), 0, 2, 0f);
    final float baseSx = read3(track.scales(), 0, 0, 1f);
    final float baseSy = read3(track.scales(), 0, 1, 1f);
    final float baseSz = read3(track.scales(), 0, 2, 1f);

    final short[] td = new short[sampleCount * 3];
    final short[] sd = new short[sampleCount * 3];
    final short[] rq = new short[sampleCount * 3];
    final byte[] rm = new byte[sampleCount];

    for (int i = 0; i < sampleCount; i++) {
      final int tb = i * 3;
      td[tb] = quantizeToShort((read3(track.translations(), i, 0, 0f) - baseTx) / posStep);
      td[tb + 1] = quantizeToShort((read3(track.translations(), i, 1, 0f) - baseTy) / posStep);
      td[tb + 2] = quantizeToShort((read3(track.translations(), i, 2, 0f) - baseTz) / posStep);

      sd[tb] = quantizeToShort((read3(track.scales(), i, 0, 1f) - baseSx) / scaleStep);
      sd[tb + 1] = quantizeToShort((read3(track.scales(), i, 1, 1f) - baseSy) / scaleStep);
      sd[tb + 2] = quantizeToShort((read3(track.scales(), i, 2, 1f) - baseSz) / scaleStep);

      final float[] qn = normalize(
          read4(track.rotations(), i, 0),
          read4(track.rotations(), i, 1),
          read4(track.rotations(), i, 2),
          read4(track.rotations(), i, 3));
      final int largest = largestAbsIndex(qn);
      final float missing = qn[largest];
      final int signBit = missing >= 0f ? 1 : 0;
      final float[] kept = new float[3];
      int k = 0;
      for (int c = 0; c < 4; c++) {
        if (c != largest) {
          kept[k++] = qn[c];
        }
      }
      final int rb = i * 3;
      rq[rb] = quantizeUnit(kept[0]);
      rq[rb + 1] = quantizeUnit(kept[1]);
      rq[rb + 2] = quantizeUnit(kept[2]);
      rm[i] = (byte) (largest | (signBit << 2));
    }

    final CompressedTrackData compressed = new CompressedTrackData(
        baseTx, baseTy, baseTz,
        baseSx, baseSy, baseSz,
        td, sd, rq, rm);

    return new TransformTrack(
        track.jointIndex(),
        track.metadata(),
        new float[0],
        new float[0],
        new float[0],
        Optional.of(compressed));
  }

  private static int sampleCount(final TransformTrack track) {
    if (track.metadata().sampleCount() > 0) {
      return track.metadata().sampleCount();
    }
    final int t = track.translations().length / 3;
    final int r = track.rotations().length / 4;
    final int s = track.scales().length / 3;
    return Math.min(t, Math.min(r, s));
  }

  private static short quantizeToShort(final float v) {
    final int i = Math.round(v);
    if (i > Short.MAX_VALUE) {
      return Short.MAX_VALUE;
    }
    if (i < Short.MIN_VALUE) {
      return Short.MIN_VALUE;
    }
    return (short) i;
  }

  private static short quantizeUnit(final float v) {
    final float clamped = Math.max(-1f, Math.min(1f, v));
    return quantizeToShort(clamped * 32767f);
  }

  private static int largestAbsIndex(final float[] q) {
    int idx = 0;
    float max = Math.abs(q[0]);
    for (int i = 1; i < 4; i++) {
      final float a = Math.abs(q[i]);
      if (a > max) {
        max = a;
        idx = i;
      }
    }
    return idx;
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = 1f / (float) Math.sqrt(lenSq);
    return new float[] {x * inv, y * inv, z * inv, w * inv};
  }

  private static float read3(final float[] data, final int sampleIndex, final int axis, final float defaultValue) {
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
