package org.dynamisengine.animis.blend;

public record AddNode(BlendNode base, BlendNode additive, float weight) implements BlendNode {}
