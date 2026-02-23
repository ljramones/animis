package dev.ljramones.animis.runtime.blend;

import java.util.Map;

public record EvalContext(Map<String, Boolean> boolParams, Map<String, Float> floatParams) {}
