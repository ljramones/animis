package dev.ljramones.animis.state;

import dev.ljramones.animis.blend.BlendNode;
import java.util.List;

public record StateDef(String name, BlendNode motion, List<TransitionDef> transitions) {}
