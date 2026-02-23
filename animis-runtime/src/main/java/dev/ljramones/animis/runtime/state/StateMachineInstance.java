package dev.ljramones.animis.runtime.state;

import dev.ljramones.animis.state.StateMachineDef;

public record StateMachineInstance(StateMachineDef definition, String currentState) {}
