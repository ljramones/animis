package dev.ljramones.animis.blend;

public sealed interface ProceduralNode extends BlendNode
    permits BreathingNode, WeightShiftNode, HeadTurnNode {}
