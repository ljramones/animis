package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.clip.Clip;

public interface ClipCompressor {
  Clip compress(Clip clip);
}
