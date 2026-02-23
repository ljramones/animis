package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.clip.Clip;
import dev.ljramones.animis.retarget.RetargetMap;
import dev.ljramones.animis.skeleton.Skeleton;

public interface ClipRetargeter {
  Clip retarget(Clip source, Skeleton sourceSkeleton, Skeleton targetSkeleton, RetargetMap map);
}
