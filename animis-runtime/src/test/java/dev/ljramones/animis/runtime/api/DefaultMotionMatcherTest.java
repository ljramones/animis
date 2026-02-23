package dev.ljramones.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ljramones.animis.motion.MotionDatabase;
import dev.ljramones.animis.motion.MotionFeatureSchema;
import dev.ljramones.animis.motion.MotionFrame;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultMotionMatcherTest {
  @Test
  void findBest_selectsNearestFrameByWeightedDistance() {
    final MotionFrame f0 = new MotionFrame(0, 0f, new float[] {0f, 0f}, new float[] {0f, 0f}, new float[] {0f, 1f});
    final MotionFrame f1 = new MotionFrame(1, 0.1f, new float[] {2f, 2f}, new float[] {2f, 2f}, new float[] {1f, 0f});
    final MotionDatabase db = new MotionDatabase(List.of(), List.of(f0, f1), schema());

    final MotionQuery query = new MotionQuery(new float[] {0.1f, -0.1f}, new float[] {0f, 0f}, new float[] {0f, 1f});
    final MotionFrame best = new DefaultMotionMatcher().findBest(db, query);

    assertEquals(0, best.clipIndex());
  }

  @Test
  void findBest_weightsInfluenceSelection() {
    final MotionFrame a = new MotionFrame(0, 0f, new float[] {0f}, new float[] {10f}, new float[] {0f});
    final MotionFrame b = new MotionFrame(1, 0f, new float[] {10f}, new float[] {0f}, new float[] {0f});
    final MotionDatabase db = new MotionDatabase(List.of(), List.of(a, b), schema());

    final MotionQuery query = new MotionQuery(new float[] {0f}, new float[] {0f}, new float[] {0f});
    final MotionFrame poseWeighted = new DefaultMotionMatcher(new float[] {10f}, new float[] {1f}, new float[] {1f}).findBest(db, query);
    final MotionFrame trajWeighted = new DefaultMotionMatcher(new float[] {1f}, new float[] {10f}, new float[] {1f}).findBest(db, query);

    assertEquals(0, poseWeighted.clipIndex());
    assertEquals(1, trajWeighted.clipIndex());
  }

  @Test
  void findBest_throwsOnFeatureLengthMismatch() {
    final MotionFrame frame = new MotionFrame(0, 0f, new float[] {0f, 0f}, new float[] {0f}, new float[] {0f});
    final MotionDatabase db = new MotionDatabase(List.of(), List.of(frame), schema());
    final MotionQuery bad = new MotionQuery(new float[] {0f}, new float[] {0f}, new float[] {0f});

    assertThrows(IllegalArgumentException.class, () -> new DefaultMotionMatcher().findBest(db, bad));
  }

  private static MotionFeatureSchema schema() {
    return new MotionFeatureSchema(List.of(0), 1, 0.1f);
  }
}
