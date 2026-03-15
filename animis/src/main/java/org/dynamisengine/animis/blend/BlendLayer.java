package org.dynamisengine.animis.blend;

public record BlendLayer(String name, LayerMode mode, BoneMask mask, BlendNode root) {}
