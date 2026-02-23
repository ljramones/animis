package org.animis.blend;

import org.animis.clip.ClipId;

public record ClipNode(ClipId clipId, float speed) implements BlendNode {}
