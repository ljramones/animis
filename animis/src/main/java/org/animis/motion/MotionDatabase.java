package org.animis.motion;

import org.animis.clip.Clip;
import java.util.List;

public record MotionDatabase(
    List<Clip> clips,
    List<MotionFrame> frames,
    MotionFeatureSchema schema
) {}
