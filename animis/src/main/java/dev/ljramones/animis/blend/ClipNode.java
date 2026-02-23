package dev.ljramones.animis.blend;

import dev.ljramones.animis.clip.ClipId;

public record ClipNode(ClipId clipId, float speed) implements BlendNode {}
