package org.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.QuantizationSpec;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.runtime.sampling.DefaultClipSampler;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DefaultClipCompressorTest {
  @Test
  void compress_roundTripWithinQuantizationTolerance() {
    final Clip clip = makeClip(new ClipId("c"), 2f, 120, 0.01f, 0.01f, true);
    final Clip compressed = new DefaultClipCompressor().compress(clip);
    final Skeleton skeleton = oneJointSkeleton();
    final DefaultClipSampler sampler = new DefaultClipSampler();

    for (float t = 0f; t <= 2f; t += 0.0333f) {
      final PoseBuffer a = new PoseBuffer(1);
      final PoseBuffer b = new PoseBuffer(1);
      sampler.sample(clip, skeleton, t, Math.max(0f, t - 0.016f), true, a);
      sampler.sample(compressed, skeleton, t, Math.max(0f, t - 0.016f), true, b);
      assertTrue(Math.abs(a.localTranslations()[0] - b.localTranslations()[0]) <= 0.011f);
      assertTrue(Math.abs(a.localTranslations()[1] - b.localTranslations()[1]) <= 0.011f);
      assertTrue(Math.abs(a.localTranslations()[2] - b.localTranslations()[2]) <= 0.011f);
      assertTrue(Math.abs(a.localScales()[0] - b.localScales()[0]) <= 0.011f);
      assertTrue(quatDistance(a.localRotations(), b.localRotations()) <= 0.03f);
    }
  }

  @Test
  void compress_preservesPoseSamplingWithinEpsilon() {
    final Clip clip = makeClip(new ClipId("pose"), 1f, 61, 0.005f, 0.005f, true);
    final Clip compressed = new DefaultClipCompressor().compress(clip);
    final Skeleton skeleton = oneJointSkeleton();
    final DefaultClipSampler sampler = new DefaultClipSampler();

    final PoseBuffer raw = new PoseBuffer(1);
    final PoseBuffer cmp = new PoseBuffer(1);
    sampler.sample(clip, skeleton, 0.42f, 0.40f, false, raw);
    sampler.sample(compressed, skeleton, 0.42f, 0.40f, false, cmp);

    assertTrue(Math.abs(raw.localTranslations()[0] - cmp.localTranslations()[0]) < 0.01f);
    assertTrue(Math.abs(raw.localTranslations()[1] - cmp.localTranslations()[1]) < 0.01f);
    assertTrue(Math.abs(raw.localTranslations()[2] - cmp.localTranslations()[2]) < 0.01f);
    assertTrue(Math.abs(raw.localScales()[0] - cmp.localScales()[0]) < 0.01f);
    assertTrue(quatDistance(raw.localRotations(), cmp.localRotations()) < 0.03f);
  }

  @Test
  void compress_reducesMemoryFootprintOn60SecondClip() {
    final Clip clip = makeClip(new ClipId("long"), 60f, 3601, 0.01f, 0.01f, true);
    final long rawBytes = estimateRawBytes(clip);

    final Clip compressed = new DefaultClipCompressor().compress(clip);
    final long compressedBytes = estimateCompressedBytes(compressed);

    assertTrue(compressedBytes < rawBytes * 0.6);
  }

  private static Clip makeClip(
      final ClipId id,
      final float durationSeconds,
      final int sampleCount,
      final float posStep,
      final float scaleStep,
      final boolean quantEnabled) {
    final float[] t = new float[sampleCount * 3];
    final float[] r = new float[sampleCount * 4];
    final float[] s = new float[sampleCount * 3];
    for (int i = 0; i < sampleCount; i++) {
      final float u = i / (float) (sampleCount - 1);
      final float ang = (float) (2.0 * Math.PI * u);
      t[i * 3] = 2f * u;
      t[i * 3 + 1] = (float) Math.sin(ang);
      t[i * 3 + 2] = (float) Math.cos(ang) * 0.5f;
      final float half = ang * 0.5f;
      r[i * 4] = 0f;
      r[i * 4 + 1] = (float) Math.sin(half);
      r[i * 4 + 2] = 0f;
      r[i * 4 + 3] = (float) Math.cos(half);
      s[i * 3] = 1f + 0.1f * (float) Math.sin(ang);
      s[i * 3 + 1] = 1f;
      s[i * 3 + 2] = 1f;
    }
    final TrackMetadata metadata = new TrackMetadata(
        sampleCount / durationSeconds,
        CurveTypeHint.SAMPLED,
        sampleCount,
        durationSeconds / (sampleCount - 1f),
        new QuantizationSpec(quantEnabled, posStep, 0.0001f, scaleStep));
    final TransformTrack track = new TransformTrack(0, metadata, t, r, s, Optional.empty());
    return new Clip(id, id.value(), durationSeconds, List.of(track));
  }

  private static long estimateRawBytes(final Clip clip) {
    long bytes = 0;
    for (final TransformTrack track : clip.tracks()) {
      bytes += (long) track.translations().length * Float.BYTES;
      bytes += (long) track.rotations().length * Float.BYTES;
      bytes += (long) track.scales().length * Float.BYTES;
    }
    return bytes;
  }

  private static long estimateCompressedBytes(final Clip clip) {
    long bytes = 0;
    for (final TransformTrack track : clip.tracks()) {
      if (track.compressed().isPresent()) {
        final var c = track.compressed().get();
        bytes += 6L * Float.BYTES;
        bytes += (long) c.translationDeltas().length * Short.BYTES;
        bytes += (long) c.scaleDeltas().length * Short.BYTES;
        bytes += (long) c.rotationSmallestThree().length * Short.BYTES;
        bytes += c.rotationMeta().length;
      } else {
        bytes += (long) track.translations().length * Float.BYTES;
        bytes += (long) track.rotations().length * Float.BYTES;
        bytes += (long) track.scales().length * Float.BYTES;
      }
    }
    return bytes;
  }

  private static float quatDistance(final float[] a, final float[] b) {
    final float dot = Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]);
    return 1f - Math.min(1f, dot);
  }

  private static Skeleton oneJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }
}
