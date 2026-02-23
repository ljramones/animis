package org.animis.runtime.blend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.animis.blend.BreathingNode;
import org.animis.blend.HeadTurnNode;
import org.animis.blend.WeightShiftNode;
import org.animis.runtime.pose.PoseBuffer;
import org.animis.runtime.sampling.DefaultClipSampler;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProceduralBlendEvaluatorTest {
  @Test
  void breathingAmplitude_scalesWithExhaustionParameter() {
    final Skeleton skeleton = oneJointSkeleton();
    final DefaultBlendEvaluator evaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    final BreathingNode node = new BreathingNode(0, 0.4f, 2f, "exhaustion");

    final PoseBuffer low = new PoseBuffer(1);
    evaluator.evaluate(
        node,
        new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("animis.timeSeconds", 0.5f, "exhaustion", 0f), null, null),
        low);

    final PoseBuffer high = new PoseBuffer(1);
    evaluator.evaluate(
        node,
        new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("animis.timeSeconds", 0.5f, "exhaustion", 1f), null, null),
        high);

    final float lowAngle = quatXAngle(low.localRotations());
    final float highAngle = quatXAngle(high.localRotations());
    assertTrue(Math.abs(highAngle) > Math.abs(lowAngle));
    assertTrue(Math.abs(highAngle) > 0.1f);
  }

  @Test
  void headTurn_tracksTargetYawWithinLimits() {
    final Skeleton skeleton = oneJointSkeleton();
    final DefaultBlendEvaluator evaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    final HeadTurnNode node = new HeadTurnNode(0, "yaw", "pitch", 0.5f, 0.5f, 1.0f);
    final PoseBuffer out = new PoseBuffer(1);

    evaluator.evaluate(
        node,
        new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("yaw", 2.0f, "pitch", 0f, "animis.deltaSeconds", 0.1f), null, null),
        out);
    final float firstYaw = quatYaw(out.localRotations());
    assertTrue(firstYaw > 0f);
    assertTrue(firstYaw <= 0.11f);

    for (int i = 0; i < 20; i++) {
      evaluator.evaluate(
          node,
          new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("yaw", 2.0f, "pitch", 0f, "animis.deltaSeconds", 0.1f), null, null),
          out);
    }
    final float settledYaw = quatYaw(out.localRotations());
    assertTrue(settledYaw <= 0.5f + 1e-3f);
    assertTrue(settledYaw > 0.45f);
  }

  @Test
  void weightShift_activatesWithIdleTime() {
    final Skeleton skeleton = oneJointSkeleton();
    final DefaultBlendEvaluator evaluator = new DefaultBlendEvaluator(new DefaultClipSampler());
    final WeightShiftNode node = new WeightShiftNode(0, 0.2f, 2f, "idleTime");

    final PoseBuffer low = new PoseBuffer(1);
    evaluator.evaluate(
        node,
        new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("animis.timeSeconds", 0.5f, "idleTime", 0f), null, null),
        low);

    final PoseBuffer high = new PoseBuffer(1);
    evaluator.evaluate(
        node,
        new EvalContext(skeleton, Map.of(), Map.of(), Map.of(), Map.of(), Map.of("animis.timeSeconds", 0.5f, "idleTime", 2f), null, null),
        high);

    assertEquals(0f, low.localTranslations()[0], 1e-6f);
    assertTrue(Math.abs(high.localTranslations()[0]) > 0.05f);
  }

  private static Skeleton oneJointSkeleton() {
    return new Skeleton(
        "s",
        List.of(new Joint(0, "j0", -1, new BindTransform(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f))),
        0);
  }

  private static float quatXAngle(final float[] q) {
    final float x = q[0];
    final float w = q[3];
    return 2f * (float) Math.atan2(x, w);
  }

  private static float quatYaw(final float[] q) {
    final float y = q[1];
    final float w = q[3];
    return 2f * (float) Math.atan2(y, w);
  }
}
