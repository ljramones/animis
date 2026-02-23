package org.animis.runtime.api;

import org.animis.clip.Clip;
import org.animis.retarget.RetargetMap;
import org.animis.skeleton.Skeleton;

public interface ClipRetargeter {
  Clip retarget(Clip source, Skeleton sourceSkeleton, Skeleton targetSkeleton, RetargetMap map);
}
