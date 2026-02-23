package dev.ljramones.animis.runtime.sampling;

import dev.ljramones.animis.runtime.api.RootMotionDelta;
import java.util.List;

public record ClipSampleResult(RootMotionDelta rootMotionDelta, List<String> firedEvents) {
  public static final ClipSampleResult EMPTY = new ClipSampleResult(RootMotionDelta.ZERO, List.of());
}
