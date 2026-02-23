package org.animis.state;

import java.util.List;

public record OrExpr(List<ConditionExpr> terms) implements ConditionExpr {}
