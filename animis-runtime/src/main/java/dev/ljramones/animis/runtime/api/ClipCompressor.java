package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.clip.Clip;

public interface ClipCompressor {
  Clip compress(Clip clip);
}
