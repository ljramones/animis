package org.animis.state;

import java.util.List;

public record AndExpr(List<ConditionExpr> terms) implements ConditionExpr {}
