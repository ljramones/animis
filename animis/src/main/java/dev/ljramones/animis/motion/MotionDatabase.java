package dev.ljramones.animis.motion;

import dev.ljramones.animis.clip.Clip;
import java.util.List;

public record MotionDatabase(
    List<Clip> clips,
    List<MotionFrame> frames,
    MotionFeatureSchema schema
) {}
