package org.animis.loader;

import java.util.List;
import java.util.Map;

final class GltfJsonHelper {

  private GltfJsonHelper() {
  }

  static String stringOr(final Object value, final String fallback) {
    return value instanceof String s ? s : fallback;
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> objectList(final Object value) {
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> objectMap(final Object value) {
    return value == null ? Map.of() : (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  static List<Object> list(final Object value) {
    return value == null ? List.of() : (List<Object>) value;
  }

  static int asInt(final Object value) {
    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("Expected number, got: " + value);
    }
    return n.intValue();
  }

  static int asIntOrDefault(final Object value, final int fallback) {
    return value instanceof Number n ? n.intValue() : fallback;
  }

  static float asFloat(final Object value) {
    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("Expected number, got: " + value);
    }
    return n.floatValue();
  }

  static float[] floatArray(final Object raw, final int expected, final float[] defaults) {
    final List<Object> values = list(raw);
    if (values.isEmpty()) {
      return defaults.clone();
    }
    if (values.size() != expected) {
      throw new IllegalArgumentException("Expected " + expected + " values, got " + values.size());
    }
    final float[] out = new float[expected];
    for (int i = 0; i < expected; i++) {
      out[i] = asFloat(values.get(i));
    }
    return out;
  }
}
