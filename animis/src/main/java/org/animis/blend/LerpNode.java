package org.animis.blend;

public record LerpNode(BlendNode a, BlendNode b, String parameter) implements BlendNode {}
