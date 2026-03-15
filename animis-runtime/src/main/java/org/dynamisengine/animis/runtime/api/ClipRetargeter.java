package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.retarget.RetargetMap;
import org.dynamisengine.animis.skeleton.Skeleton;

public interface ClipRetargeter {
  Clip retarget(Clip source, Skeleton sourceSkeleton, Skeleton targetSkeleton, RetargetMap map);
}
