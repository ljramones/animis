package org.dynamisengine.animis.transform;

import java.util.List;
import java.util.Objects;

/**
 * A named animation channel driving a single float value over time.
 *
 * Linearly interpolates between keyframes. Clamps to first/last
 * keyframe outside the keyframe time range.
 *
 * <p>Use cases: position channels (posX, posY, posZ), rotation angles
 * (rotX, rotY, rotZ), uniform scale, opacity, blend weights, or any
 * other animatable float parameter.</p>
 */
public final class PropertyTrack {

    private final String name;
    private final PropertyKeyframe[] keyframes;

    public PropertyTrack(String name, PropertyKeyframe... keyframes) {
        this.name = Objects.requireNonNull(name, "name");
        this.keyframes = Objects.requireNonNull(keyframes, "keyframes").clone();
    }

    public PropertyTrack(String name, List<PropertyKeyframe> keyframes) {
        this(name, keyframes.toArray(PropertyKeyframe[]::new));
    }

    public String name() { return name; }

    public int keyframeCount() { return keyframes.length; }

    public PropertyKeyframe keyframe(int index) { return keyframes[index]; }

    /**
     * Sample the track at a given time. Linearly interpolates between keyframes.
     * Clamps to first/last keyframe outside range.
     */
    public float sample(float time) {
        if (keyframes.length == 0) return 0;
        if (keyframes.length == 1 || time <= keyframes[0].time()) return keyframes[0].value();
        if (time >= keyframes[keyframes.length - 1].time()) return keyframes[keyframes.length - 1].value();

        for (int i = 0; i < keyframes.length - 1; i++) {
            PropertyKeyframe a = keyframes[i], b = keyframes[i + 1];
            if (time >= a.time() && time <= b.time()) {
                float t = (time - a.time()) / (b.time() - a.time());
                return a.value() + t * (b.value() - a.value());
            }
        }
        return keyframes[keyframes.length - 1].value();
    }
}
