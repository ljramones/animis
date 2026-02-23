package org.animis.runtime.blend;

import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.skeleton.Skeleton;
import java.util.Collections;
import java.util.Map;

public record EvalContext(
    Skeleton skeleton,
    Map<ClipId, Clip> clips,
    Map<ClipId, Float> clipTimes,
    Map<ClipId, Boolean> clipLoops,
    Map<String, Boolean> boolParams,
    Map<String, Float> floatParams,
    RootMotionAccumulator rootMotionAccumulator,
    EventAccumulator eventAccumulator) {
  public EvalContext {
    clips = clips == null ? Collections.emptyMap() : clips;
    clipTimes = clipTimes == null ? Collections.emptyMap() : clipTimes;
    clipLoops = clipLoops == null ? Collections.emptyMap() : clipLoops;
    boolParams = boolParams == null ? Collections.emptyMap() : boolParams;
    floatParams = floatParams == null ? Collections.emptyMap() : floatParams;
  }

  public EvalContext withoutRootMotion() {
    return new EvalContext(
        this.skeleton,
        this.clips,
        this.clipTimes,
        this.clipLoops,
        this.boolParams,
        this.floatParams,
        null,
        this.eventAccumulator);
  }

  public EvalContext withoutAccumulators() {
    return new EvalContext(
        this.skeleton,
        this.clips,
        this.clipTimes,
        this.clipLoops,
        this.boolParams,
        this.floatParams,
        null,
        null);
  }
}
