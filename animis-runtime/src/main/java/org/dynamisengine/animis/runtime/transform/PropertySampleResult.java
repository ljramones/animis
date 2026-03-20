package org.dynamisengine.animis.runtime.transform;

import org.dynamisengine.animis.clip.AnimationEvent;

import java.util.List;

/**
 * Result of sampling a PropertyClip: fired events during the time advance.
 *
 * @param firedEvents events whose normalized time was crossed this frame
 */
public record PropertySampleResult(List<AnimationEvent> firedEvents) {

    public static final PropertySampleResult EMPTY = new PropertySampleResult(List.of());
}
