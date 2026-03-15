package org.dynamisengine.animis.state;

import org.dynamisengine.animis.blend.BlendNode;
import java.util.List;

public record StateDef(String name, BlendNode motion, List<TransitionDef> transitions) {}
