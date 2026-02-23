package org.animis.state;

import org.animis.blend.BlendNode;
import java.util.List;

public record StateDef(String name, BlendNode motion, List<TransitionDef> transitions) {}
