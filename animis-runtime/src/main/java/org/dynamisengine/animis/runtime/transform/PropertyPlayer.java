package org.dynamisengine.animis.runtime.transform;

import org.dynamisengine.animis.clip.AnimationEvent;
import org.dynamisengine.animis.transform.PropertyClip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Playback controller for a {@link PropertyClip}.
 *
 * Advances time, handles looping, pause, reset, speed.
 * Detects event crossings using the same logic as the skeletal
 * {@code ClipSampler}: compare previousTime→currentTime against
 * event normalizedTime, with loop-boundary handling.
 */
public final class PropertyPlayer {

    private PropertyClip clip;
    private float currentTime = 0;
    private float previousTime = 0;
    private boolean playing = true;
    private boolean looping = true;
    private float speed = 1.0f;

    // Event state: track which events fired in current loop pass
    private boolean[] eventFired;
    private final List<AnimationEvent> pendingEvents = new ArrayList<>();

    public PropertyPlayer(PropertyClip clip) {
        this.clip = Objects.requireNonNull(clip, "clip");
        this.eventFired = new boolean[clip.events().size()];
    }

    /**
     * Advance playback by dt seconds. Detects event crossings.
     * After calling, use {@link #drainEvents()} to consume fired events.
     *
     * @return sample result with fired events
     */
    public PropertySampleResult update(float dt) {
        pendingEvents.clear();
        if (!playing) return PropertySampleResult.EMPTY;

        previousTime = currentTime;
        currentTime += dt * speed;

        boolean wrapped = false;
        if (looping && clip.duration() > 0) {
            if (currentTime >= clip.duration()) {
                currentTime -= clip.duration();
                wrapped = true;
            }
            while (currentTime < 0) currentTime += clip.duration();
        } else {
            currentTime = Math.min(currentTime, clip.duration());
        }

        // Reset fired flags on loop wrap
        if (wrapped) {
            for (int i = 0; i < eventFired.length; i++) eventFired[i] = false;
        }

        // Detect event crossings
        float dur = clip.duration();
        if (dur > 0) {
            float prevNorm = previousTime / dur;
            float curNorm = currentTime / dur;
            List<AnimationEvent> events = clip.events();
            for (int i = 0; i < events.size(); i++) {
                if (eventFired[i]) continue;
                AnimationEvent evt = events.get(i);
                boolean crossed;
                if (wrapped) {
                    crossed = evt.normalizedTime() > prevNorm || evt.normalizedTime() <= curNorm;
                } else {
                    crossed = evt.normalizedTime() > prevNorm && evt.normalizedTime() <= curNorm;
                }
                if (crossed) {
                    eventFired[i] = true;
                    pendingEvents.add(evt);
                }
            }
        }

        return new PropertySampleResult(List.copyOf(pendingEvents));
    }

    /** Drain events fired this frame. */
    public List<AnimationEvent> drainEvents() {
        return List.copyOf(pendingEvents);
    }

    /** Sample a named channel at the current playback time. */
    public float sample(String channel) {
        return clip.sample(channel, currentTime);
    }

    /** Sample a named channel with a default value. */
    public float sample(String channel, float defaultValue) {
        return clip.sample(channel, currentTime, defaultValue);
    }

    public void setClip(PropertyClip newClip) {
        this.clip = Objects.requireNonNull(newClip);
        this.eventFired = new boolean[newClip.events().size()];
        reset();
    }

    public void togglePause() { playing = !playing; }
    public void toggleLoop() { looping = !looping; }
    public void setSpeed(float s) { speed = s; }

    public void reset() {
        currentTime = 0;
        previousTime = 0;
        pendingEvents.clear();
        for (int i = 0; i < eventFired.length; i++) eventFired[i] = false;
    }

    public PropertyClip clip() { return clip; }
    public boolean isPlaying() { return playing; }
    public boolean isLooping() { return looping; }
    public float speed() { return speed; }
    public float currentTime() { return currentTime; }
    public float duration() { return clip.duration(); }
    public String clipName() { return clip.name(); }
}
