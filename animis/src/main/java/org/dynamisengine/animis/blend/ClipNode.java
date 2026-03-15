package org.dynamisengine.animis.blend;

import org.dynamisengine.animis.clip.ClipId;

public record ClipNode(ClipId clipId, float speed) implements BlendNode {}
