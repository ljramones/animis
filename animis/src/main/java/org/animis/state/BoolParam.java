package org.animis.state;

public record BoolParam(String name, boolean expected) implements ConditionExpr {}
