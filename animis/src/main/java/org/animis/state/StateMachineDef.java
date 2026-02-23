package org.animis.state;

import java.util.List;

public record StateMachineDef(String name, List<StateDef> states, String entryState) {}
