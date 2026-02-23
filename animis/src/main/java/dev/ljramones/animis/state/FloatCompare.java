package dev.ljramones.animis.state;

public record FloatCompare(String name, CompareOp op, float value) implements ConditionExpr {}
