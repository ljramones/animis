package dev.ljramones.animis.runtime.blend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ljramones.animis.blend.BlendLayer;
import dev.ljramones.animis.blend.BoneMask;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.LayerMode;
import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.clip.CurveTypeHint;
import dev.ljramones.animis.clip.QuantizationSpec;
import dev.ljramones.animis.clip.TrackMetadata;
import dev.ljramones.animis.clip.TransformTrack;
import dev.ljramones.animis.runtime.pose.PoseBuffer;
import dev.ljramones.animis.runtime.sampling.DefaultClipSampler;
import dev.ljramones.animis.skeleton.BindTransform;
import dev.ljramones.animis.skeleton.Joint;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LayeredBlendEvaluatorTest {
  @Test
  void overrideLayer_usesPerJointMaskWeights() {
    final ClipId baseId = new ClipId("base");
    final ClipId upperId = new ClipId("upper");
    final Skeleton skeleton = twoJointSkeleton();

    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(baseId, clip(baseId, 0f, 0f), upperId, clip(upperId, 10f, 20f)),
        Map.of(baseId, 0f, upperId, 0f),
        Map.of(baseId, false, upperId, false),
        Map.of(),
        Map.of(),
        null,
        null);

    final BlendLayer base = new BlendLayer("base", LayerMode.OVERRIDE, new BoneMask("full", new float[] {1f, 1f}), new ClipNode(baseId, 1f));
    final BlendLayer upper = new BlendLayer("upper", LayerMode.OVERRIDE, new BoneMask("mask", new float[] {1f, 0.5f}), new ClipNode(upperId, 1f));

    final PoseBuffer out = new PoseBuffer(2);
    final LayeredBlendEvaluator evaluator = new LayeredBlendEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    evaluator.evaluate(List.of(base, upper), ctx, out);

    assertEquals(10f, out.localTranslations()[0], 1e-5f);
    assertEquals(10f, out.localTranslations()[3], 1e-5f);
  }

  @Test
  void additiveLayer_appliesWeightedAdditiveOffsets() {
    final ClipId baseId = new ClipId("base");
    final ClipId addId = new ClipId("add");
    final Skeleton skeleton = twoJointSkeleton();

    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(baseId, clip(baseId, 2f, 4f), addId, clip(addId, 8f, 12f)),
        Map.of(baseId, 0f, addId, 0f),
        Map.of(baseId, false, addId, false),
        Map.of(),
        Map.of(),
        null,
        null);

    final BlendLayer base = new BlendLayer("base", LayerMode.OVERRIDE, new BoneMask("full", new float[] {1f, 1f}), new ClipNode(baseId, 1f));
    final BlendLayer add = new BlendLayer("add", LayerMode.ADDITIVE, new BoneMask("mask", new float[] {0.25f, 0.5f}), new ClipNode(addId, 1f));

    final PoseBuffer out = new PoseBuffer(2);
    final LayeredBlendEvaluator evaluator = new LayeredBlendEvaluator(new DefaultBlendEvaluator(new DefaultClipSampler()));
    evaluator.evaluate(List.of(base, add), ctx, out);

    assertEquals(4f, out.localTranslations()[0], 1e-5f);
    assertEquals(10f, out.localTranslations()[3], 1e-5f);
  }

  private static Skeleton twoJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(
            new Joint(0, "j0", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)),
            new Joint(1, "j1", 0, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Clip clip(final ClipId id, final float joint0X, final float joint1X) {
    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        1,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack j0 = new TransformTrack(
        0,
        metadata,
        new float[] {joint0X, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    final TransformTrack j1 = new TransformTrack(
        1,
        metadata,
        new float[] {joint1X, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f});
    return new Clip(id, id.value(), 1f, List.of(j0, j1));
  }
}
