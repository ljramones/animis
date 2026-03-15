package org.dynamisengine.animis.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

final class BvhAnimationLoaderTest {
  @Test
  void load_minimalBvh_parsesHierarchyAndClip() throws Exception {
    final AnimationLoadResult result = new BvhAnimationLoader().load(
        new ByteArrayInputStream(minimalBvh().getBytes()),
        "bvh");

    assertEquals(1, result.skeletons().size());
    assertEquals(3, result.skeletons().get(0).joints().size());
    assertEquals(-1, result.skeletons().get(0).joints().get(0).parentIndex());
    assertEquals(0, result.skeletons().get(0).joints().get(1).parentIndex());
    assertEquals(1, result.skeletons().get(0).joints().get(2).parentIndex());

    assertEquals(1, result.clips().size());
    assertEquals(5, result.clips().get(0).tracks().get(0).metadata().sampleCount());
    assertEquals(0.0333333f, result.clips().get(0).tracks().get(0).metadata().sampleIntervalSeconds(), 1e-6f);
  }

  @Test
  void load_quaternionsAreUnitLength() throws Exception {
    final AnimationLoadResult result = new BvhAnimationLoader().load(
        new ByteArrayInputStream(minimalBvh().getBytes()),
        "bvh");
    final float[] rotations = result.clips().get(0).tracks().get(0).rotations();
    for (int i = 0; i < rotations.length; i += 4) {
      final float lenSq = rotations[i] * rotations[i] + rotations[i + 1] * rotations[i + 1]
          + rotations[i + 2] * rotations[i + 2] + rotations[i + 3] * rotations[i + 3];
      assertTrue(Math.abs(1f - (float) Math.sqrt(lenSq)) < 1e-4f);
    }
  }

  private static String minimalBvh() {
    return """
        HIERARCHY
        ROOT Hips
        {
          OFFSET 0.0 0.0 0.0
          CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation
          JOINT Spine
          {
            OFFSET 0.0 10.0 0.0
            CHANNELS 3 Zrotation Xrotation Yrotation
            JOINT Head
            {
              OFFSET 0.0 8.0 0.0
              CHANNELS 3 Zrotation Xrotation Yrotation
              End Site
              {
                OFFSET 0.0 2.0 0.0
              }
            }
          }
        }
        MOTION
        Frames: 5
        Frame Time: 0.0333333
        0 0 0 0 0 0  0 0 0  0 0 0
        0 0 0 10 0 0  5 0 0  2 0 0
        0 0 0 20 0 0  10 0 0  4 0 0
        0 0 0 30 0 0  15 0 0  6 0 0
        0 0 0 40 0 0  20 0 0  8 0 0
        """;
  }
}
