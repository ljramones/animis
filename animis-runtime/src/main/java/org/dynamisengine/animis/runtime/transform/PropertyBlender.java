package org.dynamisengine.animis.runtime.transform;

import org.dynamisengine.animis.transform.PropertyClip;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Crossfade blender between two {@link PropertyClip}s.
 *
 * Manages two independent playback times and a blend weight that
 * transitions from source (weight=0) to target (weight=1) over
 * a fixed duration.
 *
 * <p>Sampling returns the weighted interpolation of both clips
 * for any named channel. Channels present in only one clip use
 * that clip's value at the appropriate weight.</p>
 */
public final class PropertyBlender {

    private PropertyClip sourceClip;
    private PropertyClip targetClip;
    private float sourceTime = 0;
    private float targetTime = 0;
    private float blendWeight = 1.0f; // start fully on initial clip
    private float blendDuration = 0;
    private float blendElapsed = 0;
    private boolean blending = false;
    private boolean playing = true;
    private boolean looping = true;
    private float speed = 1.0f;

    public PropertyBlender(PropertyClip initialClip) {
        this.sourceClip = Objects.requireNonNull(initialClip);
        this.targetClip = initialClip;
    }

    /**
     * Begin a crossfade to a new clip over the given duration (seconds).
     */
    public void transitionTo(PropertyClip newClip, float duration) {
        Objects.requireNonNull(newClip, "newClip");
        if (blending) {
            // Collapse current blend: target becomes new source
            sourceClip = targetClip;
            sourceTime = targetTime;
        }
        targetClip = newClip;
        targetTime = 0;
        blendDuration = duration;
        blendElapsed = 0;
        blendWeight = 0;
        blending = true;
    }

    public void update(float dt) {
        if (!playing) return;
        float scaledDt = dt * speed;

        // Advance both clip times
        sourceTime = advanceTime(sourceTime, scaledDt, sourceClip.duration());
        targetTime = advanceTime(targetTime, scaledDt, targetClip.duration());

        // Advance blend weight (real-time, not speed-scaled)
        if (blending) {
            blendElapsed += dt;
            blendWeight = Math.min(blendElapsed / blendDuration, 1.0f);
            if (blendWeight >= 1.0f) {
                // Handoff: target becomes authoritative
                sourceClip = targetClip;
                sourceTime = targetTime;
                blendWeight = 1.0f;
                blending = false;
            }
        }
    }

    /**
     * Sample a named channel with crossfade blending.
     */
    public float sample(String channel) {
        return sample(channel, 0f);
    }

    /**
     * Sample a named channel with crossfade blending and a default value.
     */
    public float sample(String channel, float defaultValue) {
        float sourceVal = sourceClip.sample(channel, sourceTime, defaultValue);
        float targetVal = targetClip.sample(channel, targetTime, defaultValue);

        if (!blending || blendWeight >= 1.0f) return targetVal;
        if (blendWeight <= 0f) return sourceVal;

        return sourceVal * (1f - blendWeight) + targetVal * blendWeight;
    }

    /** Get all channel names across both clips. */
    public Set<String> allChannels() {
        Set<String> names = new HashSet<>(sourceClip.tracks().keySet());
        names.addAll(targetClip.tracks().keySet());
        return names;
    }

    public void togglePause() { playing = !playing; }
    public void toggleLoop() { looping = !looping; }
    public void setSpeed(float s) { speed = s; }

    public void reset() {
        sourceTime = 0; targetTime = 0;
        blendWeight = 1.0f; blending = false; blendElapsed = 0;
    }

    public boolean isPlaying() { return playing; }
    public boolean isLooping() { return looping; }
    public boolean isBlending() { return blending; }
    public float speed() { return speed; }
    public float blendWeight() { return blendWeight; }
    public String sourceClipName() { return sourceClip.name(); }
    public String targetClipName() { return targetClip.name(); }
    public float sourceTime() { return sourceTime; }
    public float targetTime() { return targetTime; }

    private float advanceTime(float time, float dt, float duration) {
        if (duration <= 0) return 0;
        time += dt;
        if (looping) {
            while (time >= duration) time -= duration;
            while (time < 0) time += duration;
        } else {
            time = Math.min(time, duration);
        }
        return time;
    }
}
