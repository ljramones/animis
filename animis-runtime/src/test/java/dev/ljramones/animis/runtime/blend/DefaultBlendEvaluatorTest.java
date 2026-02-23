package dev.ljramones.animis.runtime.blend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ljramones.animis.blend.AddNode;
import dev.ljramones.animis.blend.ClipNode;
import dev.ljramones.animis.blend.LerpNode;
import dev.ljramones.animis.blend.OneDChild;
import dev.ljramones.animis.blend.OneDNode;
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

final class DefaultBlendEvaluatorTest {
  @Test
  void evaluatesClipNodeUsingPerClipTime() {
    final ClipId clipId = new ClipId("base");
    final Clip clip = clip(clipId, 0f, 10f);
    final Skeleton skeleton = skeleton();

    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(clipId, clip),
        Map.of(clipId, 0.5f),
        Map.of(clipId, false),
        Map.of(),
        Map.of(),
        null);

    final PoseBuffer out = new PoseBuffer(1);
    new DefaultBlendEvaluator(new DefaultClipSampler()).evaluate(new ClipNode(clipId, 1f), ctx, out);

    assertEquals(5f, out.localTranslations()[0], 1e-5f);
  }

  @Test
  void lerpNodeBlendsByFloatParameter() {
    final ClipId a = new ClipId("a");
    final ClipId b = new ClipId("b");
    final Skeleton skeleton = skeleton();
    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(a, clip(a, 0f, 0f), b, clip(b, 10f, 10f)),
        Map.of(a, 0f, b, 0f),
        Map.of(a, false, b, false),
        Map.of(),
        Map.of("speed", 0.25f),
        null);

    final PoseBuffer out = new PoseBuffer(1);
    final LerpNode node = new LerpNode(new ClipNode(a, 1f), new ClipNode(b, 1f), "speed");
    new DefaultBlendEvaluator(new DefaultClipSampler()).evaluate(node, ctx, out);

    assertEquals(2.5f, out.localTranslations()[0], 1e-5f);
  }

  @Test
  void oneDNodeChoosesThresholdRangeAndBlends() {
    final ClipId idle = new ClipId("idle");
    final ClipId run = new ClipId("run");
    final Skeleton skeleton = skeleton();
    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(idle, clip(idle, 0f, 0f), run, clip(run, 20f, 20f)),
        Map.of(idle, 0f, run, 0f),
        Map.of(idle, false, run, false),
        Map.of(),
        Map.of("speed", 0.5f),
        null);

    final OneDNode node = new OneDNode(
        "speed",
        List.of(
            new OneDChild(0f, new ClipNode(idle, 1f)),
            new OneDChild(1f, new ClipNode(run, 1f))));

    final PoseBuffer out = new PoseBuffer(1);
    new DefaultBlendEvaluator(new DefaultClipSampler()).evaluate(node, ctx, out);

    assertEquals(10f, out.localTranslations()[0], 1e-5f);
  }

  @Test
  void addNodeAppliesWeightedOverlay() {
    final ClipId base = new ClipId("base");
    final ClipId add = new ClipId("add");
    final Skeleton skeleton = skeleton();
    final EvalContext ctx = new EvalContext(
        skeleton,
        Map.of(base, clip(base, 2f, 2f), add, clip(add, 4f, 4f)),
        Map.of(base, 0f, add, 0f),
        Map.of(base, false, add, false),
        Map.of(),
        Map.of(),
        null);

    final AddNode node = new AddNode(new ClipNode(base, 1f), new ClipNode(add, 1f), 0.5f);
    final PoseBuffer out = new PoseBuffer(1);
    new DefaultBlendEvaluator(new DefaultClipSampler()).evaluate(node, ctx, out);

    assertEquals(4f, out.localTranslations()[0], 1e-5f);
  }

  private static Skeleton skeleton() {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "root", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static Clip clip(final ClipId id, final float x0, final float x1) {
    final TrackMetadata metadata = new TrackMetadata(
        1f,
        CurveTypeHint.SAMPLED,
        2,
        1f,
        new QuantizationSpec(false, 0f, 0f, 0f));
    final TransformTrack track = new TransformTrack(
        0,
        metadata,
        new float[] {x0, 0f, 0f, x1, 0f, 0f},
        new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f},
        new float[] {1f, 1f, 1f, 1f, 1f, 1f});
    return new Clip(id, id.value(), 1f, List.of(track));
  }
}
