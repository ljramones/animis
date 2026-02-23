package org.animis.blend;

public record AddNode(BlendNode base, BlendNode additive, float weight) implements BlendNode {}
