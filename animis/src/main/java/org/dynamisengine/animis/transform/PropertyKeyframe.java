package org.dynamisengine.animis.transform;

/**
 * A single keyframe in a property animation track.
 *
 * @param time  time in seconds from clip start
 * @param value the float value at this keyframe
 */
public record PropertyKeyframe(float time, float value) {}
