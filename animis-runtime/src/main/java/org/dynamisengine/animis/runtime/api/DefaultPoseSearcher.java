package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.motion.PoseSearchIndex;
import org.dynamisengine.animis.motion.PoseTag;
import org.dynamisengine.animis.motion.TaggedFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultPoseSearcher implements PoseSearcher {
  @Override
  public List<TaggedFrame> query(final PoseSearchIndex index, final List<PoseTag> requiredTags) {
    if (index == null) {
      throw new IllegalArgumentException("index must be non-null");
    }
    final List<PoseTag> requirements = requiredTags == null ? List.of() : requiredTags;
    if (requirements.isEmpty()) {
      return List.copyOf(index.frames());
    }

    final ArrayList<TaggedFrame> matches = new ArrayList<>();
    for (final TaggedFrame frame : index.frames()) {
      if (matchesAll(frame.tags(), requirements)) {
        matches.add(frame);
      }
    }
    return List.copyOf(matches);
  }

  @Override
  public Optional<TaggedFrame> queryFirst(final PoseSearchIndex index, final List<PoseTag> requiredTags) {
    if (index == null) {
      throw new IllegalArgumentException("index must be non-null");
    }
    final List<PoseTag> requirements = requiredTags == null ? List.of() : requiredTags;
    if (requirements.isEmpty()) {
      return index.frames().stream().findFirst();
    }
    for (final TaggedFrame frame : index.frames()) {
      if (matchesAll(frame.tags(), requirements)) {
        return Optional.of(frame);
      }
    }
    return Optional.empty();
  }

  private static boolean matchesAll(final List<PoseTag> frameTags, final List<PoseTag> requirements) {
    for (final PoseTag required : requirements) {
      if (!matchesAny(frameTags, required)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAny(final List<PoseTag> frameTags, final PoseTag required) {
    for (final PoseTag tag : frameTags) {
      if (!tag.key().equals(required.key())) {
        continue;
      }
      if ("*".equals(required.value()) || tag.value().equals(required.value())) {
        return true;
      }
    }
    return false;
  }
}
