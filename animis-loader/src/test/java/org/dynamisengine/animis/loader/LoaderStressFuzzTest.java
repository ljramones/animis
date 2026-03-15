package org.dynamisengine.animis.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class LoaderStressFuzzTest {
  @Test
  void gltf_skinWithZeroJoints_throwsClearError() {
    final String json = minimalGltf().replace("\"joints\":[0,1]", "\"joints\":[]");
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(json.getBytes()), "gltf"));
    assertTrue(ex.getMessage().contains("joints"));
  }

  @Test
  void gltf_animationTargetsMissingNode_throwsClearError() {
    final String json = minimalGltf().replace("\"node\":1", "\"node\":99");
    final IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new GltfAnimationLoader().load(new ByteArrayInputStream(json.getBytes()), "gltf"));
    assertTrue(ex.getMessage().contains("not part of any skin joints"));
  }

  @Test
  void gltf_emptyAnimations_isValidAndReturnsNoClips() throws Exception {
    final String json = minimalGltf().replaceAll("\"animations\":\\[.*]}$", "\"animations\":[]}");
    final AnimationLoadResult result = new GltfAnimationLoader().load(new ByteArrayInputStream(json.getBytes()), "gltf");
    assertEquals(1, result.skeletons().size());
    assertTrue(result.clips().isEmpty());
  }

  @Test
  void bvh_zeroFrames_isValidDegenerate() throws Exception {
    final String bvh = """
        HIERARCHY
        ROOT Hips
        {
          OFFSET 0.0 0.0 0.0
          CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation
        }
        MOTION
        Frames: 0
        Frame Time: 0.0333333
        """;
    final AnimationLoadResult result = new BvhAnimationLoader().load(new ByteArrayInputStream(bvh.getBytes()), "bvh");
    assertEquals(1, result.skeletons().size());
    assertEquals(1, result.clips().size());
    assertEquals(0f, result.clips().getFirst().durationSeconds(), 1e-6f);
  }

  @Test
  void bvh_singleJoint_isValidDegenerate() throws Exception {
    final String bvh = """
        HIERARCHY
        ROOT Hips
        {
          OFFSET 0.0 0.0 0.0
          CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation
        }
        MOTION
        Frames: 1
        Frame Time: 0.0166667
        0 0 0 0 0 0
        """;
    final AnimationLoadResult result = new BvhAnimationLoader().load(new ByteArrayInputStream(bvh.getBytes()), "bvh");
    assertEquals(1, result.skeletons().size());
    assertEquals(1, result.skeletons().getFirst().joints().size());
    assertEquals(1, result.clips().size());
    assertEquals(1, result.clips().getFirst().tracks().size());
    assertFiniteAndUnitQuaternions(result.clips().getFirst().tracks().getFirst().rotations());
  }

  @Test
  void deterministicRandomGltfStructures_noUnexpectedExceptionsAndFiniteData() throws Exception {
    final Random random = new Random(0x600DF00DL);
    final GltfAnimationLoader loader = new GltfAnimationLoader();
    for (int i = 0; i < 50; i++) {
      final String json = randomGltf(random);
      final AnimationLoadResult result = loader.load(new ByteArrayInputStream(json.getBytes()), "gltf");
      assertEquals(1, result.skeletons().size());
      assertFalse(result.clips().isEmpty());
      final var clip = result.clips().getFirst();
      assertTrue(clip.durationSeconds() > 0f);
      clip.tracks().forEach(track -> {
        assertFinite(track.translations());
        assertFinite(track.rotations());
        assertFinite(track.scales());
        assertFiniteAndUnitQuaternions(track.rotations());
      });
    }
  }

  @Test
  void deterministicRandomBvhStructures_noUnexpectedExceptionsAndFiniteData() throws Exception {
    final Random random = new Random(0xBEEFCAFE);
    final BvhAnimationLoader loader = new BvhAnimationLoader();
    for (int i = 0; i < 50; i++) {
      final String bvh = randomBvh(random);
      final AnimationLoadResult result = loader.load(new ByteArrayInputStream(bvh.getBytes()), "bvh");
      assertEquals(1, result.skeletons().size());
      assertEquals(1, result.clips().size());
      final var clip = result.clips().getFirst();
      assertTrue(clip.durationSeconds() >= 0f);
      clip.tracks().forEach(track -> {
        assertFinite(track.translations());
        assertFinite(track.rotations());
        assertFinite(track.scales());
        assertFiniteAndUnitQuaternions(track.rotations());
      });
    }
  }

  private static String minimalGltf() {
    final ByteBuffer b = ByteBuffer.allocate(192).order(ByteOrder.LITTLE_ENDIAN);
    b.putFloat(0f).putFloat(1f);
    b.putFloat(0f).putFloat(0f).putFloat(0f);
    b.putFloat(1f).putFloat(0f).putFloat(0f);
    b.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f);
    b.putFloat(0f).putFloat(0.70710677f).putFloat(0f).putFloat(0.70710677f);
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0, 0, 1});
    final String b64 = Base64.getEncoder().encodeToString(b.array());

    return "{\"asset\":{\"version\":\"2.0\"},"
        + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + b64 + "\",\"byteLength\":192}],"
        + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":8},{\"buffer\":0,\"byteOffset\":8,\"byteLength\":24},"
        + "{\"buffer\":0,\"byteOffset\":32,\"byteLength\":32},{\"buffer\":0,\"byteOffset\":64,\"byteLength\":128}],"
        + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
        + "{\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"},"
        + "{\"bufferView\":2,\"componentType\":5126,\"count\":2,\"type\":\"VEC4\"},"
        + "{\"bufferView\":3,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"}],"
        + "\"nodes\":[{\"name\":\"root\",\"children\":[1]},{\"name\":\"child\"}],"
        + "\"skins\":[{\"joints\":[0,1],\"inverseBindMatrices\":3}],"
        + "\"animations\":[{\"name\":\"walk\","
        + "\"samplers\":[{\"input\":0,\"output\":1,\"interpolation\":\"LINEAR\"},{\"input\":0,\"output\":2,\"interpolation\":\"LINEAR\"}],"
        + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}},{\"sampler\":1,\"target\":{\"node\":1,\"path\":\"rotation\"}}]}]}";
  }

  private static String randomGltf(final Random random) {
    final float t0x = rand(random, -1f, 1f);
    final float t0y = rand(random, -1f, 1f);
    final float t0z = rand(random, -1f, 1f);
    final float t1x = rand(random, -1f, 1f);
    final float t1y = rand(random, -1f, 1f);
    final float t1z = rand(random, -1f, 1f);

    final float[] q0 = randomUnitQuat(random);
    final float[] q1 = randomUnitQuat(random);

    final ByteBuffer b = ByteBuffer.allocate(192).order(ByteOrder.LITTLE_ENDIAN);
    b.putFloat(0f).putFloat(1f);
    b.putFloat(t0x).putFloat(t0y).putFloat(t0z);
    b.putFloat(t1x).putFloat(t1y).putFloat(t1z);
    b.putFloat(q0[0]).putFloat(q0[1]).putFloat(q0[2]).putFloat(q0[3]);
    b.putFloat(q1[0]).putFloat(q1[1]).putFloat(q1[2]).putFloat(q1[3]);
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    putMat4(b, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -1, 0, 0, 1});
    final String b64 = Base64.getEncoder().encodeToString(b.array());

    return "{\"asset\":{\"version\":\"2.0\"},"
        + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + b64 + "\",\"byteLength\":192}],"
        + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":8},{\"buffer\":0,\"byteOffset\":8,\"byteLength\":24},"
        + "{\"buffer\":0,\"byteOffset\":32,\"byteLength\":32},{\"buffer\":0,\"byteOffset\":64,\"byteLength\":128}],"
        + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
        + "{\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"},"
        + "{\"bufferView\":2,\"componentType\":5126,\"count\":2,\"type\":\"VEC4\"},"
        + "{\"bufferView\":3,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"}],"
        + "\"nodes\":[{\"name\":\"root\",\"children\":[1]},{\"name\":\"child\"}],"
        + "\"skins\":[{\"joints\":[0,1],\"inverseBindMatrices\":3}],"
        + "\"animations\":[{\"name\":\"rand\",\"samplers\":[{\"input\":0,\"output\":1,\"interpolation\":\"LINEAR\"},"
        + "{\"input\":0,\"output\":2,\"interpolation\":\"LINEAR\"}],"
        + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}},"
        + "{\"sampler\":1,\"target\":{\"node\":1,\"path\":\"rotation\"}}]}]}";
  }

  private static String randomBvh(final Random random) {
    final StringBuilder sb = new StringBuilder();
    sb.append("HIERARCHY\n");
    sb.append("ROOT Hips\n{\n");
    sb.append("  OFFSET 0.0 0.0 0.0\n");
    sb.append("  CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation\n");
    sb.append("  JOINT Spine\n  {\n");
    sb.append("    OFFSET 0.0 10.0 0.0\n");
    sb.append("    CHANNELS 3 Zrotation Xrotation Yrotation\n");
    sb.append("    JOINT Head\n    {\n");
    sb.append("      OFFSET 0.0 8.0 0.0\n");
    sb.append("      CHANNELS 3 Zrotation Xrotation Yrotation\n");
    sb.append("      End Site\n      {\n");
    sb.append("        OFFSET 0.0 2.0 0.0\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("}\n");
    sb.append("MOTION\n");
    sb.append("Frames: 10\n");
    sb.append("Frame Time: 0.0333333\n");
    for (int i = 0; i < 10; i++) {
      for (int c = 0; c < 12; c++) {
        if (c > 0) {
          sb.append(' ');
        }
        sb.append(rand(random, -40f, 40f));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static float[] randomUnitQuat(final Random random) {
    final float x = rand(random, -1f, 1f);
    final float y = rand(random, -1f, 1f);
    final float z = rand(random, -1f, 1f);
    final float w = rand(random, -1f, 1f);
    final float inv = (float) (1.0 / Math.sqrt(Math.max(1e-8f, x * x + y * y + z * z + w * w)));
    return new float[] {x * inv, y * inv, z * inv, w * inv};
  }

  private static float rand(final Random random, final float min, final float max) {
    return min + random.nextFloat() * (max - min);
  }

  private static void putMat4(final ByteBuffer b, final float[] m) {
    for (final float v : m) {
      b.putFloat(v);
    }
  }

  private static void assertFinite(final float[] values) {
    for (final float v : values) {
      assertTrue(Float.isFinite(v));
      assertTrue(Math.abs(v) < 1_000_000f);
    }
  }

  private static void assertFiniteAndUnitQuaternions(final float[] rotations) {
    for (int i = 0; i < rotations.length; i += 4) {
      final float x = rotations[i];
      final float y = rotations[i + 1];
      final float z = rotations[i + 2];
      final float w = rotations[i + 3];
      assertTrue(Float.isFinite(x));
      assertTrue(Float.isFinite(y));
      assertTrue(Float.isFinite(z));
      assertTrue(Float.isFinite(w));
      final float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
      assertTrue(Math.abs(1f - len) <= 1e-5f);
    }
  }
}
