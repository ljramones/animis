package org.dynamisengine.animis.transform;

import org.dynamisengine.animis.clip.AnimationEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A clip of named property tracks with optional animation events.
 *
 * <p>This is the non-skeletal counterpart to {@link org.dynamisengine.animis.clip.Clip}.
 * Where {@code Clip} drives joint transforms via index-addressed tracks,
 * {@code PropertyClip} drives named float properties via string-addressed tracks.</p>
 *
 * <p>Use cases: object transform animation (position, rotation, scale),
 * material parameter animation, camera paths, UI transitions, or any
 * time-driven float parameter set.</p>
 *
 * @param name      clip name
 * @param duration  total duration in seconds
 * @param tracks    named property tracks
 * @param events    timeline event markers (sorted by normalized time)
 */
public record PropertyClip(
        String name,
        float duration,
        Map<String, PropertyTrack> tracks,
        List<AnimationEvent> events
) {
    public PropertyClip {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tracks, "tracks");
        events = events != null ? List.copyOf(events) : List.of();
    }

    public PropertyClip(String name, float duration, Map<String, PropertyTrack> tracks) {
        this(name, duration, tracks, List.of());
    }

    /**
     * Sample a named channel at the given time.
     *
     * @return the interpolated value, or {@code defaultValue} if the channel does not exist
     */
    public float sample(String channelName, float time, float defaultValue) {
        PropertyTrack track = tracks.get(channelName);
        return track != null ? track.sample(time) : defaultValue;
    }

    /**
     * Sample a named channel at the given time. Returns 0 if the channel does not exist.
     */
    public float sample(String channelName, float time) {
        return sample(channelName, time, 0f);
    }

    /** Returns true if this clip has a track with the given name. */
    public boolean hasTrack(String channelName) {
        return tracks.containsKey(channelName);
    }
}
