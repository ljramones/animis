package dev.ljramones.animis.clip;

public record QuantizationSpec(
    boolean enabled,
    float posStep,
    float rotStep,
    float scaleStep
) {}
