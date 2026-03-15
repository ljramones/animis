package org.dynamisengine.animis.motion;

import org.dynamisengine.animis.clip.Clip;
import java.util.List;

public record MotionDatabase(
    List<Clip> clips,
    List<MotionFrame> frames,
    MotionFeatureSchema schema
) {}
