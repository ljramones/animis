package org.dynamisengine.animis.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class GltfAnimationLoaderTest {
  @Test
  void load_minimalGltf_parsesSkeletonAndClip() throws Exception {
    final AnimationLoadResult result = new GltfAnimationLoader().load(
        new ByteArrayInputStream(minimalGltf().getBytes()),
        "gltf");

    assertEquals(1, result.skeletons().size());
    assertEquals(2, result.skeletons().get(0).joints().size());
    assertEquals(-1, result.skeletons().get(0).joints().get(0).parentIndex());
    assertEquals(0, result.skeletons().get(0).joints().get(1).parentIndex());
    assertEquals(1f, result.skeletons().get(0).joints().get(1).bind().tx(), 1e-4f);
    assertEquals(1, result.clips().size());
    assertTrue(result.clips().get(0).durationSeconds() > 0f);
  }

  @Test
  void load_multipleAnimations_returnsMultipleClips() throws Exception {
    final AnimationLoadResult result = new GltfAnimationLoader().load(
        new ByteArrayInputStream(multiAnimationGltf().getBytes()),
        "gltf");
    assertEquals(2, result.clips().size());
  }

  @Test
  void load_missingSkin_throwsClearMessage() {
    final String json = """
        {"asset":{"version":"2.0"},"nodes":[{"name":"root"}],"animations":[]}
        """;
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(json.getBytes()), "gltf"));
    assertTrue(ex.getMessage().contains("skins"));
  }

  @Test
  void load_unsupportedAccessorType_throws() {
    final String json = minimalGltf().replace("\"componentType\":5126", "\"componentType\":5123");
    assertThrows(
        IllegalArgumentException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(json.getBytes()), "gltf"));
  }

  @Test
  void load_minimalGlb_parsesSkeletonAndClip() throws Exception {
    final AnimationLoadResult result = new GltfAnimationLoader().load(
        new ByteArrayInputStream(minimalGlb()),
        "glb");

    assertEquals(1, result.skeletons().size());
    assertEquals(2, result.skeletons().getFirst().joints().size());
    assertEquals(1, result.clips().size());
    assertTrue(result.clips().getFirst().durationSeconds() > 0f);
  }

  @Test
  void load_glbWithoutBinChunkWithDataUri_isValid() throws Exception {
    final byte[] glb = wrapAsGlb(minimalGltf(), null);
    final AnimationLoadResult result = new GltfAnimationLoader().load(
        new ByteArrayInputStream(glb),
        "glb");
    assertEquals(1, result.skeletons().size());
    assertEquals(1, result.clips().size());
  }

  @Test
  void load_truncatedGlbHeader_throwsClearIoException() {
    final IOException ex = assertThrows(
        IOException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}), "glb"));
    assertTrue(ex.getMessage().toLowerCase().contains("truncated"));
  }

  @Test
  void load_invalidGlbMagic_throwsClearIoException() {
    final byte[] headerOnly = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(0x12345678)
        .putInt(2)
        .putInt(12)
        .array();
    final IOException ex = assertThrows(
        IOException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(headerOnly), "glb"));
    assertTrue(ex.getMessage().toLowerCase().contains("magic"));
  }

  @Test
  void load_glbAndJsonEquivalentAssets_produceMatchingResults() throws Exception {
    final String json = minimalGltf();
    final AnimationLoadResult jsonResult = new GltfAnimationLoader().load(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
        "gltf");
    final AnimationLoadResult glbResult = new GltfAnimationLoader().load(
        new ByteArrayInputStream(minimalGlb()),
        "glb");

    assertEquals(jsonResult.skeletons().size(), glbResult.skeletons().size());
    assertEquals(jsonResult.clips().size(), glbResult.clips().size());
    assertEquals(jsonResult.clips().getFirst().durationSeconds(), glbResult.clips().getFirst().durationSeconds(), 1e-6f);
    assertEquals(jsonResult.clips().getFirst().tracks().size(), glbResult.clips().getFirst().tracks().size());
    final var jsonTrack = jsonResult.clips().getFirst().tracks().getFirst();
    final var glbTrack = glbResult.clips().getFirst().tracks().getFirst();
    assertEquals(jsonTrack.jointIndex(), glbTrack.jointIndex());
    assertEquals(jsonTrack.metadata().sampleCount(), glbTrack.metadata().sampleCount());
    assertEquals(jsonTrack.translations()[0], glbTrack.translations()[0], 1e-6f);
    assertEquals(jsonTrack.rotations()[0], glbTrack.rotations()[0], 1e-6f);
  }

  private static String minimalGltf() {
    final byte[] bytes = minimalBinary(false);
    final String b64 = Base64.getEncoder().encodeToString(bytes);
    return """
        {
          "asset":{"version":"2.0"},
          "buffers":[{"uri":"data:application/octet-stream;base64,%s","byteLength":192}],
          "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":8},
            {"buffer":0,"byteOffset":8,"byteLength":24},
            {"buffer":0,"byteOffset":32,"byteLength":32},
            {"buffer":0,"byteOffset":64,"byteLength":128}
          ],
          "accessors":[
            {"bufferView":0,"componentType":5126,"count":2,"type":"SCALAR"},
            {"bufferView":1,"componentType":5126,"count":2,"type":"VEC3"},
            {"bufferView":2,"componentType":5126,"count":2,"type":"VEC4"},
            {"bufferView":3,"componentType":5126,"count":2,"type":"MAT4"}
          ],
          "nodes":[
            {"name":"root","children":[1]},
            {"name":"child"}
          ],
          "skins":[{"joints":[0,1],"inverseBindMatrices":3}],
          "animations":[
            {"name":"walk",
             "samplers":[{"input":0,"output":1,"interpolation":"LINEAR"},{"input":0,"output":2,"interpolation":"LINEAR"}],
             "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}},{"sampler":1,"target":{"node":1,"path":"rotation"}}]
            }
          ]
        }
        """.formatted(b64);
  }

  private static String multiAnimationGltf() {
    final byte[] bytes = minimalBinary(false);
    final String b64 = Base64.getEncoder().encodeToString(bytes);
    return "{\"asset\":{\"version\":\"2.0\"},"
        + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + b64 + "\",\"byteLength\":192}],"
        + "\"bufferViews\":["
        + "{\"buffer\":0,\"byteOffset\":0,\"byteLength\":8},"
        + "{\"buffer\":0,\"byteOffset\":8,\"byteLength\":24},"
        + "{\"buffer\":0,\"byteOffset\":32,\"byteLength\":32},"
        + "{\"buffer\":0,\"byteOffset\":64,\"byteLength\":128}"
        + "],"
        + "\"accessors\":["
        + "{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
        + "{\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"},"
        + "{\"bufferView\":2,\"componentType\":5126,\"count\":2,\"type\":\"VEC4\"},"
        + "{\"bufferView\":3,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"}"
        + "],"
        + "\"nodes\":[{\"name\":\"root\",\"children\":[1]},{\"name\":\"child\"}],"
        + "\"skins\":[{\"joints\":[0,1],\"inverseBindMatrices\":3}],"
        + "\"animations\":["
        + "{\"name\":\"walk\",\"samplers\":[{\"input\":0,\"output\":1,\"interpolation\":\"LINEAR\"},{\"input\":0,\"output\":2,\"interpolation\":\"LINEAR\"}],"
        + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}},{\"sampler\":1,\"target\":{\"node\":1,\"path\":\"rotation\"}}]},"
        + "{\"name\":\"run\",\"samplers\":[{\"input\":0,\"output\":1,\"interpolation\":\"STEP\"}],"
        + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}}]}"
        + "]}";
  }

  private static byte[] minimalBinary(final boolean withSecondAnimation) {
    final ByteBuffer b = ByteBuffer.allocate(withSecondAnimation ? 216 : 192).order(ByteOrder.LITTLE_ENDIAN);
    b.putFloat(0f).putFloat(1f); // times
    b.putFloat(0f).putFloat(0f).putFloat(0f); // translation0
    b.putFloat(1f).putFloat(0f).putFloat(0f); // translation1
    b.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f); // rotation0
    b.putFloat(0f).putFloat(0.70710677f).putFloat(0f).putFloat(0.70710677f); // rotation1
    if (withSecondAnimation) {
      b.putFloat(0f).putFloat(0f).putFloat(0f);
      b.putFloat(2f).putFloat(0f).putFloat(0f);
    }
    // inverse bind matrices: root=I, child=translate(-1,0,0)
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0, 0, 1});
    return b.array();
  }

  private static void putMat4(final ByteBuffer b, final float[] m) {
    for (final float v : m) {
      b.putFloat(v);
    }
  }

  private static byte[] minimalGlb() {
    final String json = minimalGlbJson();
    final byte[] bin = minimalBinary(false);
    return wrapAsGlb(json, bin);
  }

  private static String minimalGlbJson() {
    return """
        {
          "asset":{"version":"2.0"},
          "buffers":[{"byteLength":192}],
          "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":8},
            {"buffer":0,"byteOffset":8,"byteLength":24},
            {"buffer":0,"byteOffset":32,"byteLength":32},
            {"buffer":0,"byteOffset":64,"byteLength":128}
          ],
          "accessors":[
            {"bufferView":0,"componentType":5126,"count":2,"type":"SCALAR"},
            {"bufferView":1,"componentType":5126,"count":2,"type":"VEC3"},
            {"bufferView":2,"componentType":5126,"count":2,"type":"VEC4"},
            {"bufferView":3,"componentType":5126,"count":2,"type":"MAT4"}
          ],
          "nodes":[
            {"name":"root","children":[1]},
            {"name":"child"}
          ],
          "skins":[{"joints":[0,1],"inverseBindMatrices":3}],
          "animations":[
            {"name":"walk",
             "samplers":[{"input":0,"output":1,"interpolation":"LINEAR"},{"input":0,"output":2,"interpolation":"LINEAR"}],
             "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}},{"sampler":1,"target":{"node":1,"path":"rotation"}}]
            }
          ]
        }
        """;
  }

  private static byte[] wrapAsGlb(final String json, final byte[] bin) {
    byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
    final int jsonPad = (4 - (jsonBytes.length % 4)) % 4;
    if (jsonPad > 0) {
      final byte[] padded = new byte[jsonBytes.length + jsonPad];
      System.arraycopy(jsonBytes, 0, padded, 0, jsonBytes.length);
      for (int i = jsonBytes.length; i < padded.length; i++) {
        padded[i] = 0x20;
      }
      jsonBytes = padded;
    }

    byte[] binBytes = bin;
    if (binBytes != null) {
      final int binPad = (4 - (binBytes.length % 4)) % 4;
      if (binPad > 0) {
        final byte[] padded = new byte[binBytes.length + binPad];
        System.arraycopy(binBytes, 0, padded, 0, binBytes.length);
        binBytes = padded;
      }
    }

    final int totalLength = 12 + 8 + jsonBytes.length + (binBytes == null ? 0 : 8 + binBytes.length);
    final ByteBuffer out = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(0x46546C67);
    out.putInt(2);
    out.putInt(totalLength);
    out.putInt(jsonBytes.length);
    out.putInt(0x4E4F534A);
    out.put(jsonBytes);
    if (binBytes != null) {
      out.putInt(binBytes.length);
      out.putInt(0x004E4942);
      out.put(binBytes);
    }
    return out.array();
  }
}
