package org.animis.state;

public record FloatCompare(String name, CompareOp op, float value) implements ConditionExpr {}
