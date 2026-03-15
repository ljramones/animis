package org.dynamisengine.animis.ik;

import java.util.ArrayList;
import java.util.List;

public record FabrikChainDef(
    String name,
    List<Integer> joints,
    float tolerance,
    int maxIterations,
    List<float[]> angleLimits
) {
  public FabrikChainDef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must be non-blank");
    }
    joints = joints == null ? List.of() : List.copyOf(joints);
    if (joints.isEmpty()) {
      throw new IllegalArgumentException("joints must not be empty");
    }
    if (tolerance < 0f) {
      throw new IllegalArgumentException("tolerance must be >= 0");
    }
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be > 0");
    }

    final List<float[]> limits = new ArrayList<>();
    if (angleLimits != null) {
      for (int i = 0; i < angleLimits.size(); i++) {
        final float[] raw = angleLimits.get(i);
        if (raw == null) {
          limits.add(null);
          continue;
        }
        if (raw.length != 2) {
          throw new IllegalArgumentException("angleLimits[" + i + "] must have length 2");
        }
        limits.add(new float[] {raw[0], raw[1]});
      }
    }
    angleLimits = List.copyOf(limits);
  }
}
