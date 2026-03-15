package org.dynamisengine.animis.clip;

public record TrackMetadata(
    float sourceFps,
    CurveTypeHint sourceCurveType,
    int sampleCount,
    float sampleIntervalSeconds,
    QuantizationSpec quantization
) {}
