package org.animis.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
}
