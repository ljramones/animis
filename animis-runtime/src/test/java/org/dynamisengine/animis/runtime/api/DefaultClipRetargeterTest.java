package org.dynamisengine.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.ClipId;
import org.dynamisengine.animis.clip.CurveTypeHint;
import org.dynamisengine.animis.clip.QuantizationSpec;
import org.dynamisengine.animis.clip.TrackMetadata;
import org.dynamisengine.animis.clip.TransformTrack;
import org.dynamisengine.animis.retarget.JointMapping;
import org.dynamisengine.animis.retarget.RetargetMap;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.runtime.sampling.DefaultClipSampler;
import org.dynamisengine.animis.skeleton.BindTransform;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DefaultClipRetargeterTest {
  @Test
  void retarget_mapsJointNamesToTargetIndices() {
    final Skeleton source = new Skeleton(
        "src",
        List.of(
            new Joint(0, "hip", -1, bind(0f, 0f, 0f)),
            new Joint(1, "spine", 0, bind(1f, 0f, 0f))),
        0);
    final Skeleton target = new Skeleton(
        "dst",
        List.of(
            new Joint(0, "root", -1, bind(0f, 0f, 0f)),
            new Joint(1, "pelvis", 0, bind(1f, 0f, 0f)),
            new Joint(2, "chest", 1, bind(1f, 0f, 0f))),
        0);
    final Clip sourceClip = new Clip(
        new ClipId("c"),
        "c",
        1f,
        List.of(track(0, 1f), track(1, 2f)));
    final RetargetMap map = new RetargetMap(
        List.of(
            new JointMapping("hip", "pelvis", Optional.empty()),
            new JointMapping("spine", "chest", Optional.empty())),
        false);

    final Clip retargeted = new DefaultClipRetargeter().retarget(sourceClip, source, target, map);

    assertTrue(retargeted.tracks().stream().anyMatch(t -> t.jointIndex() == 1));
    assertTrue(retargeted.tracks().stream().anyMatch(t -> t.jointIndex() == 2));
  }

  @Test
  void retarget_scalesTranslationsByLimbRatio() {
    final Skeleton source = new Skeleton(
        "src",
        List.of(new Joint(0, "hip", -1, bind(1f, 0f, 0f))),
        0);
    final Skeleton target = new Skeleton(
        "dst",
        List.of(new Joint(0, "pelvis", -1, bind(2f, 0f, 0f))),
        0);
    final Clip sourceClip = new Clip(new ClipId("c"), "c", 1f, List.of(track(0, 1f)));
    final RetargetMap map = new RetargetMap(
        List.of(new JointMapping("hip", "pelvis", Optional.empty())),
        true);

    final Clip retargeted = new DefaultClipRetargeter().retarget(sourceClip, source, target, map);
    final TransformTrack t = retargeted.tracks().getFirst();

    assertEquals(2f, t.translations()[0], 1e-5f);
  }

  @Test
  void retarget_missingTargetJointsFallBackToBindPose() {
    final Skeleton source = new Skeleton(
        "src",
        List.of(new Joint(0, "hip", -1, bind(0f, 0f, 0f))),
        0);
    final Skeleton target = new Skeleton(
        "dst",
        List.of(
            new Joint(0, "pelvis", -1, bind(0f, 0f, 0f)),
            new Joint(1, "extra", 0, bind(5f, 0f, 0f))),
        0);
    final Clip sourceClip = new Clip(new ClipId("c"), "c", 1f, List.of(track(0, 1f)));
    final RetargetMap map = new RetargetMap(
        List.of(new JointMapping("hip", "pelvis", Optional.empty())),
        false);

    final Clip retargeted = new DefaultClipRetargeter().retarget(sourceClip, source, target, map);
    final PoseBuffer out = new PoseBuffer(2);
    new DefaultClipSampler().sample(retargeted, target, 0f, 0f, false, out);

    assertEquals(5f, out.localTranslations()[3], 1e-5f);
  }

  @Test
  void retarget_appliesRotationOffset() {
    final Skeleton source = new Skeleton(
        "src",
        List.of(new Joint(0, "head", -1, bind(0f, 0f, 0f))),
        0);
    final Skeleton target = new Skeleton(
        "dst",
        List.of(new Joint(0, "headDst", -1, bind(0f, 0f, 0f))),
        0);
    final TransformTrack srcTrack = new TransformTrack(
        0,
        metadata(),
        new float[] {0f, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    final Clip sourceClip = new Clip(new ClipId("c"), "c", 1f, List.of(srcTrack));
    final float[] yaw180 = new float[] {0f, 1f, 0f, 0f};
    final RetargetMap map = new RetargetMap(
        List.of(new JointMapping("head", "headDst", Optional.of(yaw180))),
        false);

    final Clip retargeted = new DefaultClipRetargeter().retarget(sourceClip, source, target, map);
    final float[] q = retargeted.tracks().getFirst().rotations();

    assertTrue(Math.abs(q[1]) > 0.99f);
    assertTrue(Math.abs(q[3]) < 0.01f);
  }

  private static TransformTrack track(final int jointIndex, final float tx) {
    return new TransformTrack(
        jointIndex,
        metadata(),
        new float[] {tx, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
  }

  private static TrackMetadata metadata() {
    return new TrackMetadata(1f, CurveTypeHint.SAMPLED, 1, 1f, new QuantizationSpec(false, 0f, 0f, 0f));
  }

  private static BindTransform bind(final float x, final float y, final float z) {
    return new BindTransform(x, y, z, 0f, 0f, 0f, 1f, 1f, 1f, 1f);
  }
}
