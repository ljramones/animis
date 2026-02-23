package dev.ljramones.animis.runtime.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.clip.CurveTypeHint;
import dev.ljramones.animis.clip.QuantizationSpec;
import dev.ljramones.animis.clip.TrackMetadata;
import dev.ljramones.animis.clip.TransformTrack;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultClipSamplerTest {
  @Test
  void sample_appliesBindPoseForUntrackedJoints() {
    final Skeleton skeleton = new Skeleton(
        "test",
        List.of(
            new Joint(0, "root", -1, new BindTransform(1f, 2f, 3f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "child", 0, new BindTransform(4f, 5f, 6f, 0f, 0f, 0f, 1f, 2f, 2f, 2f))),
        0);

    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        2,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack rootTrack = new TransformTrack(
        0,
        metadata,
        new float[] {0f, 0f, 0f, 10f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f});
    final Clip clip = new Clip(new ClipId("walk"), "walk", 1f, List.of(rootTrack));

    final PoseBuffer out = new PoseBuffer(2);
    new DefaultClipSampler().sample(clip, skeleton, 0.5f, false, out);

    final float[] t = out.localTranslations();
    assertEquals(5f, t[0], 1e-5f);
    assertEquals(0f, t[1], 1e-5f);
    assertEquals(0f, t[2], 1e-5f);

    assertEquals(4f, t[3], 1e-5f);
    assertEquals(5f, t[4], 1e-5f);
    assertEquals(6f, t[5], 1e-5f);
  }

  @Test
  void sample_wrapsWhenLoopEnabled() {
    final Skeleton skeleton = new Skeleton(
        "test",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);

    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        2,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {0f, 0f, 0f, 10f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f});
    final Clip clip = new Clip(new ClipId("loop"), "loop", 1f, List.of(track));

    final PoseBuffer out = new PoseBuffer(1);
    new DefaultClipSampler().sample(clip, skeleton, 1.25f, true, out);

    assertEquals(2.5f, out.localTranslations()[0], 1e-5f);
  }

  @Test
  void sample_defaultsScaleToOneWhenScaleTrackMissing() {
    final Skeleton skeleton = new Skeleton(
        "test",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 3f, 3f, 3f))),
        0);

    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        2,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {0f, 0f, 0f, 2f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[0]);
    final Clip clip = new Clip(new ClipId("scale"), "scale", 1f, List.of(track));

    final PoseBuffer out = new PoseBuffer(1);
    new DefaultClipSampler().sample(clip, skeleton, 0.5f, false, out);

    assertEquals(1f, out.localScales()[0], 1e-5f);
    assertEquals(1f, out.localScales()[1], 1e-5f);
    assertEquals(1f, out.localScales()[2], 1e-5f);
  }

  @Test
  void sample_throwsWhenTrackJointIsOutOfBounds() {
    final Skeleton skeleton = new Skeleton(
        "test",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        1,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack invalidTrack = new TransformTrack(
        1,
        metadata,
        new float[] {0f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    final Clip clip = new Clip(new ClipId("invalid"), "invalid", 1f, List.of(invalidTrack));

    final PoseBuffer out = new PoseBuffer(1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultClipSampler().sample(clip, skeleton, 0f, false, out));
  }
}
