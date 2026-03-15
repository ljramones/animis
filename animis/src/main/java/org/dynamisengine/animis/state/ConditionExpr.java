package org.dynamisengine.animis.state;

public sealed interface ConditionExpr permits BoolParam, FloatCompare, AndExpr, OrExpr, NotExpr {}
