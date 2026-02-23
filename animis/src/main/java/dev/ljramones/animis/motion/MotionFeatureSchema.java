package dev.ljramones.animis.motion;

import java.util.List;

public record MotionFeatureSchema(
    List<Integer> poseJoints,
    int trajectorySamples,
    float trajectoryDeltaSeconds
) {}
