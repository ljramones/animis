package org.dynamisengine.animis.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.animis.motion.PoseSearchIndex;
import org.dynamisengine.animis.motion.PoseTag;
import org.dynamisengine.animis.motion.TaggedFrame;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultPoseSearcherTest {
  private final PoseSearcher searcher = new DefaultPoseSearcher();

  @Test
  void query_exactTagMatch_returnsCorrectFrames() {
    final PoseSearchIndex index = index(
        frame(0, 0.1f, tag("foot.left", "planted")),
        frame(0, 0.2f, tag("foot.left", "air")));

    final List<TaggedFrame> result = searcher.query(index, List.of(tag("foot.left", "planted")));

    assertEquals(1, result.size());
    assertEquals(0.1f, result.getFirst().timeSeconds(), 1e-6f);
  }

  @Test
  void query_wildcardValue_matchesAnyValueForKey() {
    final PoseSearchIndex index = index(
        frame(0, 0.1f, tag("hand.right", "reaching")),
        frame(0, 0.2f, tag("hand.right", "idle")),
        frame(0, 0.3f, tag("foot.left", "planted")));

    final List<TaggedFrame> result = searcher.query(index, List.of(tag("hand.right", "*")));

    assertEquals(2, result.size());
  }

  @Test
  void query_multipleRequiredTags_andsConditions() {
    final PoseSearchIndex index = index(
        frame(0, 0.1f, tag("foot.left", "planted"), tag("hand.right", "reaching")),
        frame(0, 0.2f, tag("foot.left", "planted")),
        frame(0, 0.3f, tag("hand.right", "reaching")));

    final List<TaggedFrame> result = searcher.query(
        index,
        List.of(tag("foot.left", "planted"), tag("hand.right", "reaching")));

    assertEquals(1, result.size());
    assertEquals(0.1f, result.getFirst().timeSeconds(), 1e-6f);
  }

  @Test
  void query_emptyRequiredTags_returnsAllFrames() {
    final PoseSearchIndex index = index(
        frame(0, 0.1f, tag("a", "b")),
        frame(0, 0.2f, tag("c", "d")));

    final List<TaggedFrame> result = searcher.query(index, List.of());

    assertEquals(2, result.size());
  }

  @Test
  void queryFirst_returnsFirstMatchOrEmpty() {
    final PoseSearchIndex index = index(
        frame(0, 0.1f, tag("hand.right", "idle")),
        frame(0, 0.2f, tag("hand.right", "reaching")));

    assertTrue(searcher.queryFirst(index, List.of(tag("hand.right", "reaching"))).isPresent());
    assertTrue(searcher.queryFirst(index, List.of(tag("hand.right", "blocked"))).isEmpty());
  }

  private static PoseSearchIndex index(final TaggedFrame... frames) {
    return new PoseSearchIndex(List.of(frames));
  }

  private static TaggedFrame frame(final int clipIndex, final float timeSeconds, final PoseTag... tags) {
    return new TaggedFrame(clipIndex, timeSeconds, List.of(tags));
  }

  private static PoseTag tag(final String key, final String value) {
    return new PoseTag(key, value);
  }
}
