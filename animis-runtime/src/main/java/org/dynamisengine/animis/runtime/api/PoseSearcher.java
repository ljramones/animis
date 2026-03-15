package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.motion.PoseSearchIndex;
import org.dynamisengine.animis.motion.PoseTag;
import org.dynamisengine.animis.motion.TaggedFrame;
import java.util.List;
import java.util.Optional;

public interface PoseSearcher {
  List<TaggedFrame> query(PoseSearchIndex index, List<PoseTag> requiredTags);

  Optional<TaggedFrame> queryFirst(PoseSearchIndex index, List<PoseTag> requiredTags);
}
