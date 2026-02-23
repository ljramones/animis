package dev.ljramones.animis.runtime.blend;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.clip.ClipId;
import dev.ljramones.animis.skeleton.Skeleton;
import java.util.Collections;
import java.util.Map;

public record EvalContext(
    Skeleton skeleton,
    Map<ClipId, Clip> clips,
    Map<ClipId, Float> clipTimes,
    Map<ClipId, Boolean> clipLoops,
    Map<String, Boolean> boolParams,
    Map<String, Float> floatParams) {
  public EvalContext {
    clips = clips == null ? Collections.emptyMap() : clips;
    clipTimes = clipTimes == null ? Collections.emptyMap() : clipTimes;
    clipLoops = clipLoops == null ? Collections.emptyMap() : clipLoops;
    boolParams = boolParams == null ? Collections.emptyMap() : boolParams;
    floatParams = floatParams == null ? Collections.emptyMap() : floatParams;
  }
}
