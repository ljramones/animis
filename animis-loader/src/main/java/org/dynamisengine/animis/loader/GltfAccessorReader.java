package org.dynamisengine.animis.loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.dynamisengine.animis.loader.GltfJsonHelper.*;

final class GltfAccessorReader {

  private GltfAccessorReader() {
  }

  record Accessor(
      int buffer,
      int viewOffset,
      int byteStride,
      int count,
      int accessorOffset,
      int components
  ) {}

  static Accessor accessor(
      final List<Map<String, Object>> accessors,
      final List<Map<String, Object>> views,
      final int accessorIndex) {
    final Map<String, Object> accessor = accessors.get(accessorIndex);
    final int viewIndex = asInt(accessor.get("bufferView"));
    final Map<String, Object> view = views.get(viewIndex);
    final int componentType = asInt(accessor.get("componentType"));
    if (componentType != 5126) {
      throw new IllegalArgumentException("Unsupported accessor componentType: " + componentType);
    }
    final int components = switch (stringOr(accessor.get("type"), "")) {
      case "SCALAR" -> 1;
      case "VEC2" -> 2;
      case "VEC3" -> 3;
      case "VEC4" -> 4;
      case "MAT4" -> 16;
      default -> throw new IllegalArgumentException("Unsupported accessor type");
    };
    return new Accessor(
        asInt(view.get("buffer")),
        asIntOrDefault(view.get("byteOffset"), 0),
        asIntOrDefault(view.get("byteStride"), components * 4),
        asInt(accessor.get("count")),
        asIntOrDefault(accessor.get("byteOffset"), 0),
        components);
  }

  static float[] readFloatAccessorData(final Accessor accessor, final List<ByteBuffer> buffers) {
    final ByteBuffer buffer = buffers.get(accessor.buffer).duplicate().order(ByteOrder.LITTLE_ENDIAN);
    final float[] out = new float[accessor.count * accessor.components];
    int cursor = accessor.viewOffset + accessor.accessorOffset;
    for (int i = 0; i < accessor.count; i++) {
      for (int c = 0; c < accessor.components; c++) {
        out[i * accessor.components + c] = buffer.getFloat(cursor + c * 4);
      }
      cursor += accessor.byteStride;
    }
    return out;
  }

  static List<ByteBuffer> loadBuffers(final Path baseDir, final Map<String, Object> root, final byte[] embeddedBin)
      throws IOException {
    final List<Map<String, Object>> buffers = objectList(root.get("buffers"));
    final ArrayList<ByteBuffer> out = new ArrayList<>(buffers.size());
    for (final Map<String, Object> buffer : buffers) {
      final String uri = stringOr(buffer.get("uri"), null);
      if (uri == null || uri.isBlank()) {
        if (embeddedBin == null) {
          throw new IllegalArgumentException("Buffer URI missing and no embedded GLB BIN chunk present");
        }
        out.add(ByteBuffer.wrap(embeddedBin).order(ByteOrder.LITTLE_ENDIAN));
        continue;
      }
      final byte[] bytes = readUri(baseDir, uri);
      out.add(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }
    return out;
  }

  static byte[] readUri(final Path baseDir, final String uri) throws IOException {
    if (uri.startsWith("data:")) {
      final int comma = uri.indexOf(',');
      if (comma < 0) {
        throw new IllegalArgumentException("Invalid data URI");
      }
      final String metadata = uri.substring(0, comma);
      final String payload = uri.substring(comma + 1);
      if (metadata.endsWith(";base64")) {
        return Base64.getDecoder().decode(payload);
      }
      return payload.getBytes(StandardCharsets.UTF_8);
    }
    if (baseDir == null) {
      throw new IllegalArgumentException("Relative URI requires a base directory");
    }
    return Files.readAllBytes(baseDir.resolve(uri).normalize());
  }
}
