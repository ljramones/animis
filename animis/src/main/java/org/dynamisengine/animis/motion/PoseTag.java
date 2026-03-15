package org.dynamisengine.animis.motion;

public record PoseTag(String key, String value) {
  public PoseTag {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key must be non-blank");
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must be non-blank");
    }
  }
}
