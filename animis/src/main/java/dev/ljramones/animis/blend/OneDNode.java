package dev.ljramones.animis.blend;

import java.util.List;

public record OneDNode(String parameter, List<OneDChild> children) implements BlendNode {}
