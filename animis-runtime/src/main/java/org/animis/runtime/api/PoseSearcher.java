package org.animis.runtime.api;

import org.animis.motion.PoseSearchIndex;
import org.animis.motion.PoseTag;
import org.animis.motion.TaggedFrame;
import java.util.List;
import java.util.Optional;

public interface PoseSearcher {
  List<TaggedFrame> query(PoseSearchIndex index, List<PoseTag> requiredTags);

  Optional<TaggedFrame> queryFirst(PoseSearchIndex index, List<PoseTag> requiredTags);
}
