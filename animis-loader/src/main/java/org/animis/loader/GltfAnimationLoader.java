package org.animis.loader;

import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.meshforge.loader.gltf.read.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GltfAnimationLoader implements AnimationLoader {
  private static final float DEFAULT_INTERVAL = 1.0f / 30.0f;
  private static final int GLB_MAGIC = 0x46546C67;
  private static final int GLB_VERSION = 2;
  private static final int CHUNK_JSON = 0x4E4F534A;
  private static final int CHUNK_BIN = 0x004E4942;

  @Override
  public AnimationLoadResult load(final Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must be non-null");
    }
    final byte[] bytes = Files.readAllBytes(path);
    final String name = path.getFileName().toString().toLowerCase();
    if (name.endsWith(".glb") || isGlb(bytes)) {
      return loadGlb(bytes, path.getParent());
    }
    return loadGltfJson(new String(bytes, StandardCharsets.UTF_8), path.getParent(), null);
  }

  @Override
  public AnimationLoadResult load(final InputStream stream, final String formatHint) throws IOException {
    if (stream == null) {
      throw new IllegalArgumentException("stream must be non-null");
    }
    final byte[] bytes = stream.readAllBytes();
    final String hint = formatHint == null ? "" : formatHint.toLowerCase();
    if (hint.contains("glb") || isGlb(bytes)) {
      return loadGlb(bytes, null);
    }
    return loadGltfJson(new String(bytes, StandardCharsets.UTF_8), null, null);
  }

  private AnimationLoadResult loadGltfJson(final String json, final Path baseDir, final byte[] embeddedBin) throws IOException {
    final Map<String, Object> root = MiniJson.parseObject(json);
    final List<Map<String, Object>> nodes = objectList(root.get("nodes"));
    final List<Map<String, Object>> skins = objectList(root.get("skins"));
    final List<Map<String, Object>> animations = objectList(root.get("animations"));
    final List<Map<String, Object>> accessors = objectList(root.get("accessors"));
    final List<Map<String, Object>> views = objectList(root.get("bufferViews"));
    final List<ByteBuffer> buffers = loadBuffers(baseDir, root, embeddedBin);
    final Map<Integer, Integer> parentNodeMap = buildParentNodeMap(nodes);

    if (skins.isEmpty()) {
      throw new IllegalArgumentException("Missing skins[]; animation skeleton cannot be built");
    }

    final ArrayList<SkinContext> skinContexts = new ArrayList<>();
    final Set<Integer> allJointNodes = new HashSet<>();
    for (int skinIndex = 0; skinIndex < skins.size(); skinIndex++) {
      final SkinContext context = buildSkinContext(skinIndex, skins.get(skinIndex), nodes, parentNodeMap, accessors, views, buffers);
      skinContexts.add(context);
      allJointNodes.addAll(context.nodeToJoint.keySet());
    }

    validateChannelTargets(animations, allJointNodes);

    final ArrayList<Clip> clips = new ArrayList<>();
    final HashMap<Integer, Integer> clipToSkeleton = new HashMap<>();
    for (int animIndex = 0; animIndex < animations.size(); animIndex++) {
      final Map<String, Object> animation = animations.get(animIndex);
      final String baseName = stringOr(animation.get("name"), "animation_" + animIndex);
      for (int skinIndex = 0; skinIndex < skinContexts.size(); skinIndex++) {
        final SkinContext skin = skinContexts.get(skinIndex);
        final Clip clip = buildClipForSkin(
            animation, baseName, skin, accessors, views, buffers, animIndex, skinIndex);
        if (clip == null) {
          continue;
        }
        final int clipIndex = clips.size();
        clips.add(clip);
        clipToSkeleton.put(clipIndex, skinIndex);
      }
    }

    final List<Skeleton> skeletons = skinContexts.stream().map(ctx -> ctx.skeleton).toList();
    return new AnimationLoadResult(skeletons, clips, clipToSkeleton);
  }

  private static boolean isGlb(final byte[] bytes) {
    if (bytes.length < 4) {
      return false;
    }
    final int magic = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    return magic == GLB_MAGIC;
  }

  private AnimationLoadResult loadGlb(final byte[] bytes, final Path baseDir) throws IOException {
    if (bytes.length < 12) {
      throw new IOException("Truncated GLB header");
    }
    final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    final int magic = buffer.getInt();
    if (magic != GLB_MAGIC) {
      throw new IOException("Invalid GLB magic header");
    }
    final int version = buffer.getInt();
    if (version != GLB_VERSION) {
      throw new IOException("Unsupported GLB version: " + version);
    }
    final int length = buffer.getInt();
    if (length != bytes.length) {
      throw new IOException("GLB length mismatch");
    }

    byte[] jsonChunk = null;
    byte[] binChunk = null;
    while (buffer.remaining() > 0) {
      if (buffer.remaining() < 8) {
        throw new IOException("Truncated GLB chunk header");
      }
      final int chunkLength = buffer.getInt();
      final int chunkType = buffer.getInt();
      if (chunkLength < 0 || chunkLength > buffer.remaining()) {
        throw new IOException("Invalid GLB chunk length");
      }
      final byte[] data = new byte[chunkLength];
      buffer.get(data);
      if (chunkType == CHUNK_JSON) {
        if (jsonChunk != null) {
          throw new IOException("GLB contains multiple JSON chunks");
        }
        jsonChunk = data;
      } else if (chunkType == CHUNK_BIN && binChunk == null) {
        binChunk = data;
      }
    }
    if (jsonChunk == null) {
      throw new IOException("GLB missing JSON chunk");
    }
    final String json = sanitizeJsonChunk(jsonChunk);
    return loadGltfJson(json, baseDir, binChunk);
  }

  private static String sanitizeJsonChunk(final byte[] jsonChunk) {
    int end = jsonChunk.length;
    while (end > 0 && jsonChunk[end - 1] == 0) {
      end--;
    }
    return new String(jsonChunk, 0, end, StandardCharsets.UTF_8);
  }

  private static SkinContext buildSkinContext(
      final int skinIndex,
      final Map<String, Object> skin,
      final List<Map<String, Object>> nodes,
      final Map<Integer, Integer> parentNodeMap,
      final List<Map<String, Object>> accessors,
      final List<Map<String, Object>> views,
      final List<ByteBuffer> buffers) {
    final List<Object> skinJoints = list(skin.get("joints"));
    if (skinJoints.isEmpty()) {
      throw new IllegalArgumentException("skin[" + skinIndex + "] joints is empty");
    }

    final HashMap<Integer, Integer> nodeToJoint = new HashMap<>();
    final ArrayList<Integer> jointNodes = new ArrayList<>(skinJoints.size());
    for (int i = 0; i < skinJoints.size(); i++) {
      final int nodeIndex = asInt(skinJoints.get(i));
      nodeToJoint.put(nodeIndex, i);
      jointNodes.add(nodeIndex);
    }

    final float[] inverseBind = readInverseBindMatrices(skin, accessors, views, buffers, jointNodes.size());
    final float[][] worldBind = inverseBind == null ? null : invertAllMat4(inverseBind, jointNodes.size());

    final ArrayList<Joint> joints = new ArrayList<>(jointNodes.size());
    final float[][] localBindMatrices = new float[jointNodes.size()][16];
    for (int i = 0; i < jointNodes.size(); i++) {
      final int nodeIndex = jointNodes.get(i);
      final int parentNode = parentNodeMap.getOrDefault(nodeIndex, -1);
      final int parentJoint = parentNode < 0 ? -1 : nodeToJoint.getOrDefault(parentNode, -1);

      final BindTransform bind;
      if (worldBind != null) {
        final float[] localMatrix;
        if (parentJoint < 0) {
          localMatrix = worldBind[i];
        } else {
          localMatrix = mul(invertMat4(worldBind[parentJoint]), worldBind[i]);
        }
        localBindMatrices[i] = localMatrix;
        bind = decomposeBind(localMatrix);
      } else {
        bind = bindFromNode(nodes.get(nodeIndex));
      }
      final String name = stringOr(nodes.get(nodeIndex).get("name"), "joint_" + i);
      joints.add(new Joint(i, name, parentJoint, bind));
    }

    int rootJoint = 0;
    for (int i = 0; i < joints.size(); i++) {
      if (joints.get(i).parentIndex() < 0) {
        rootJoint = i;
        break;
      }
    }
    return new SkinContext(new Skeleton("skin_" + skinIndex, joints, rootJoint), nodeToJoint);
  }

  private static Clip buildClipForSkin(
      final Map<String, Object> animation,
      final String baseName,
      final SkinContext skin,
      final List<Map<String, Object>> accessors,
      final List<Map<String, Object>> views,
      final List<ByteBuffer> buffers,
      final int animIndex,
      final int skinIndex) {
    final List<Map<String, Object>> channels = objectList(animation.get("channels"));
    final List<Map<String, Object>> samplers = objectList(animation.get("samplers"));
    if (channels.isEmpty() || samplers.isEmpty()) {
      return null;
    }

    final ArrayList<ChannelSample> samples = new ArrayList<>();
    float duration = 0f;
    float minInterval = Float.MAX_VALUE;
    for (int c = 0; c < channels.size(); c++) {
      final Map<String, Object> channel = channels.get(c);
      final Map<String, Object> target = objectMap(channel.get("target"));
      final int nodeIndex = asInt(target.get("node"));
      final Integer jointIndex = skin.nodeToJoint.get(nodeIndex);
      if (jointIndex == null) {
        continue;
      }
      final String path = stringOr(target.get("path"), "");
      if (!path.equals("translation") && !path.equals("rotation") && !path.equals("scale")) {
        continue;
      }

      final int samplerIndex = asInt(channel.get("sampler"));
      final Map<String, Object> sampler = samplers.get(samplerIndex);
      final String interpolation = stringOr(sampler.get("interpolation"), "LINEAR");
      final int inputAccessor = asInt(sampler.get("input"));
      final int outputAccessor = asInt(sampler.get("output"));
      final Accessor input = accessor(accessors, views, inputAccessor);
      final Accessor output = accessor(accessors, views, outputAccessor);
      if (input.components != 1) {
        throw new IllegalArgumentException("Sampler input accessor must be SCALAR");
      }
      final int expectedComponents = path.equals("rotation") ? 4 : 3;
      if (output.components != expectedComponents) {
        throw new IllegalArgumentException("Accessor component count mismatch for path " + path);
      }
      if (output.count != input.count) {
        throw new IllegalArgumentException("Sampler input/output keyframe count mismatch");
      }
      final float[] times = readFloatAccessorData(input, buffers);
      final float[] values = readFloatAccessorData(output, buffers);
      if (times.length == 0) {
        continue;
      }
      duration = Math.max(duration, times[times.length - 1]);
      for (int i = 1; i < times.length; i++) {
        final float d = times[i] - times[i - 1];
        if (d > 1e-6f) {
          minInterval = Math.min(minInterval, d);
        }
      }
      samples.add(new ChannelSample(jointIndex, path, interpolation, times, values, expectedComponents));
    }

    if (samples.isEmpty() || duration <= 0f) {
      return null;
    }
    if (minInterval == Float.MAX_VALUE) {
      minInterval = DEFAULT_INTERVAL;
    }
    final int sampleCount = Math.max(2, 1 + (int) Math.floor(duration / minInterval));
    final HashMap<Integer, TrackBuilder> tracks = new HashMap<>();
    for (final Joint joint : skin.skeleton.joints()) {
      tracks.put(joint.index(), TrackBuilder.fromBind(joint, sampleCount));
    }

    for (int s = 0; s < sampleCount; s++) {
      final float time = Math.min(duration, s * minInterval);
      for (final ChannelSample sample : samples) {
        final TrackBuilder track = tracks.get(sample.jointIndex);
        if (track != null) {
          track.apply(sample.path, s, sample.valueAt(time));
        }
      }
    }

    final ArrayList<TransformTrack> out = new ArrayList<>(tracks.size());
    CurveTypeHint hint = CurveTypeHint.SAMPLED;
    for (final ChannelSample sample : samples) {
      if (sample.interpolation.equals("CUBICSPLINE")) {
        hint = CurveTypeHint.HERMITE;
        break;
      }
    }
    final float sourceFps = averageFps(samples);
    for (final TrackBuilder track : tracks.values()) {
      final TrackMetadata metadata = new TrackMetadata(sourceFps, hint, sampleCount, minInterval, null);
      out.add(new TransformTrack(
          track.jointIndex,
          metadata,
          track.translations,
          normalizeQuaternionArray(track.rotations),
          track.scales));
    }

    final String clipName = skinIndex == 0 ? baseName : (baseName + "_skin" + skinIndex);
    return new Clip(new ClipId("anim_" + animIndex + "_skin_" + skinIndex), clipName, duration, out, Optional.empty(), List.of());
  }

  private static float averageFps(final List<ChannelSample> samples) {
    float sum = 0f;
    int count = 0;
    for (final ChannelSample sample : samples) {
      for (int i = 1; i < sample.times.length; i++) {
        final float d = sample.times[i] - sample.times[i - 1];
        if (d > 1e-6f) {
          sum += d;
          count++;
        }
      }
    }
    if (count == 0) {
      return 0f;
    }
    return 1f / (sum / count);
  }

  private static float[] readInverseBindMatrices(
      final Map<String, Object> skin,
      final List<Map<String, Object>> accessors,
      final List<Map<String, Object>> views,
      final List<ByteBuffer> buffers,
      final int expectedJoints) {
    final Object accessorObj = skin.get("inverseBindMatrices");
    if (!(accessorObj instanceof Number number)) {
      return null;
    }
    final Accessor accessor = accessor(accessors, views, number.intValue());
    if (accessor.components != 16) {
      throw new IllegalArgumentException("inverseBindMatrices accessor must be MAT4");
    }
    if (accessor.count != expectedJoints) {
      throw new IllegalArgumentException("inverseBindMatrices count does not match joint count");
    }
    return readFloatAccessorData(accessor, buffers);
  }

  private static Accessor accessor(
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

  private static float[] readFloatAccessorData(final Accessor accessor, final List<ByteBuffer> buffers) {
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

  private static List<ByteBuffer> loadBuffers(final Path baseDir, final Map<String, Object> root, final byte[] embeddedBin)
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

  private static byte[] readUri(final Path baseDir, final String uri) throws IOException {
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

  private static Map<Integer, Integer> buildParentNodeMap(final List<Map<String, Object>> nodes) {
    final HashMap<Integer, Integer> parent = new HashMap<>();
    for (int i = 0; i < nodes.size(); i++) {
      for (final Object child : list(nodes.get(i).get("children"))) {
        parent.put(asInt(child), i);
      }
    }
    return parent;
  }

  private static void validateChannelTargets(final List<Map<String, Object>> animations, final Set<Integer> jointNodes) {
    for (final Map<String, Object> animation : animations) {
      for (final Map<String, Object> channel : objectList(animation.get("channels"))) {
        final Map<String, Object> target = objectMap(channel.get("target"));
        final int nodeIndex = asInt(target.get("node"));
        if (!jointNodes.contains(nodeIndex)) {
          throw new IllegalArgumentException("Animation channel target node is not part of any skin joints: " + nodeIndex);
        }
      }
    }
  }

  private static BindTransform bindFromNode(final Map<String, Object> node) {
    final float[] t = floatArray(node.get("translation"), 3, new float[] {0f, 0f, 0f});
    final float[] r = normalizeQuaternionArray(floatArray(node.get("rotation"), 4, new float[] {0f, 0f, 0f, 1f}));
    final float[] s = floatArray(node.get("scale"), 3, new float[] {1f, 1f, 1f});
    return new BindTransform(t[0], t[1], t[2], r[0], r[1], r[2], r[3], s[0], s[1], s[2]);
  }

  private static BindTransform decomposeBind(final float[] m) {
    final float tx = m[12];
    final float ty = m[13];
    final float tz = m[14];
    final float sx = (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    final float sy = (float) Math.sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
    final float sz = (float) Math.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);
    final float r00 = m[0] / Math.max(sx, 1e-8f);
    final float r01 = m[4] / Math.max(sy, 1e-8f);
    final float r02 = m[8] / Math.max(sz, 1e-8f);
    final float r10 = m[1] / Math.max(sx, 1e-8f);
    final float r11 = m[5] / Math.max(sy, 1e-8f);
    final float r12 = m[9] / Math.max(sz, 1e-8f);
    final float r20 = m[2] / Math.max(sx, 1e-8f);
    final float r21 = m[6] / Math.max(sy, 1e-8f);
    final float r22 = m[10] / Math.max(sz, 1e-8f);
    final float[] q = quatFromRotationMatrix(r00, r01, r02, r10, r11, r12, r20, r21, r22);
    return new BindTransform(tx, ty, tz, q[0], q[1], q[2], q[3], sx, sy, sz);
  }

  private static float[] quatFromRotationMatrix(
      final float m00, final float m01, final float m02,
      final float m10, final float m11, final float m12,
      final float m20, final float m21, final float m22) {
    final float trace = m00 + m11 + m22;
    float x;
    float y;
    float z;
    float w;
    if (trace > 0f) {
      final float s = (float) Math.sqrt(trace + 1f) * 2f;
      w = 0.25f * s;
      x = (m21 - m12) / s;
      y = (m02 - m20) / s;
      z = (m10 - m01) / s;
    } else if (m00 > m11 && m00 > m22) {
      final float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
      w = (m21 - m12) / s;
      x = 0.25f * s;
      y = (m01 + m10) / s;
      z = (m02 + m20) / s;
    } else if (m11 > m22) {
      final float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
      w = (m02 - m20) / s;
      x = (m01 + m10) / s;
      y = 0.25f * s;
      z = (m12 + m21) / s;
    } else {
      final float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
      w = (m10 - m01) / s;
      x = (m02 + m20) / s;
      y = (m12 + m21) / s;
      z = 0.25f * s;
    }
    return normalizeQuaternionArray(new float[] {x, y, z, w});
  }

  private static float[][] invertAllMat4(final float[] mats, final int count) {
    final float[][] out = new float[count][16];
    for (int i = 0; i < count; i++) {
      final float[] m = new float[16];
      System.arraycopy(mats, i * 16, m, 0, 16);
      out[i] = invertMat4(m);
    }
    return out;
  }

  private static float[] invertMat4(final float[] m) {
    final float[] inv = new float[16];
    inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15]
        + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
    inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15]
        - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
    inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15]
        + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
    inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14]
        - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
    inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15]
        - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
    inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15]
        + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
    inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15]
        - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
    inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14]
        + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
    inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15]
        + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
    inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15]
        - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
    inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15]
        + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
    inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14]
        - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
    inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11]
        - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
    inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11]
        + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
    inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11]
        - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
    inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10]
        + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
    float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
    if (Math.abs(det) < 1e-8f) {
      throw new IllegalArgumentException("Matrix is not invertible");
    }
    det = 1f / det;
    for (int i = 0; i < 16; i++) {
      inv[i] *= det;
    }
    return inv;
  }

  private static float[] mul(final float[] a, final float[] b) {
    final float[] out = new float[16];
    for (int col = 0; col < 4; col++) {
      for (int row = 0; row < 4; row++) {
        out[col * 4 + row] =
            a[0 * 4 + row] * b[col * 4 + 0]
                + a[1 * 4 + row] * b[col * 4 + 1]
                + a[2 * 4 + row] * b[col * 4 + 2]
                + a[3 * 4 + row] * b[col * 4 + 3];
      }
    }
    return out;
  }

  private static float[] normalizeQuaternionArray(final float[] q) {
    final float lenSq = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3];
    if (lenSq <= 1e-10f) {
      q[0] = 0f;
      q[1] = 0f;
      q[2] = 0f;
      q[3] = 1f;
      return q;
    }
    final float inv = (float) (1.0 / Math.sqrt(lenSq));
    q[0] *= inv;
    q[1] *= inv;
    q[2] *= inv;
    q[3] *= inv;
    return q;
  }

  private static float[] floatArray(final Object raw, final int expected, final float[] defaults) {
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

  private static String stringOr(final Object value, final String fallback) {
    return value instanceof String s ? s : fallback;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> objectList(final Object value) {
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMap(final Object value) {
    return value == null ? Map.of() : (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(final Object value) {
    return value == null ? List.of() : (List<Object>) value;
  }

  private static int asInt(final Object value) {
    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("Expected number, got: " + value);
    }
    return n.intValue();
  }

  private static int asIntOrDefault(final Object value, final int fallback) {
    return value instanceof Number n ? n.intValue() : fallback;
  }

  private static float asFloat(final Object value) {
    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("Expected number, got: " + value);
    }
    return n.floatValue();
  }

  private record Accessor(
      int buffer,
      int viewOffset,
      int byteStride,
      int count,
      int accessorOffset,
      int components
  ) {}

  private record SkinContext(Skeleton skeleton, Map<Integer, Integer> nodeToJoint) {}

  private static final class ChannelSample {
    private final int jointIndex;
    private final String path;
    private final String interpolation;
    private final float[] times;
    private final float[] values;
    private final int components;

    private ChannelSample(
        final int jointIndex,
        final String path,
        final String interpolation,
        final float[] times,
        final float[] values,
        final int components) {
      this.jointIndex = jointIndex;
      this.path = path;
      this.interpolation = interpolation;
      this.times = times;
      this.values = values;
      this.components = components;
    }

    private float[] valueAt(final float time) {
      if (this.times.length == 1 || time <= this.times[0]) {
        return key(0);
      }
      final int last = this.times.length - 1;
      if (time >= this.times[last]) {
        return key(last);
      }
      int right = 1;
      while (right < this.times.length && this.times[right] < time) {
        right++;
      }
      final int left = right - 1;
      final float t0 = this.times[left];
      final float t1 = this.times[right];
      final float alpha = (time - t0) / Math.max(1e-6f, t1 - t0);
      final float[] a = key(left);
      if (this.interpolation.equals("STEP")) {
        return a;
      }
      final float[] b = key(right);
      if (this.path.equals("rotation")) {
        return slerp(a, b, alpha);
      }
      return lerp(a, b, alpha);
    }

    private float[] key(final int index) {
      final float[] out = new float[this.components];
      System.arraycopy(this.values, index * this.components, out, 0, this.components);
      return out;
    }

    private static float[] lerp(final float[] a, final float[] b, final float t) {
      final float[] out = new float[a.length];
      for (int i = 0; i < a.length; i++) {
        out[i] = a[i] + (b[i] - a[i]) * t;
      }
      return out;
    }

    private static float[] slerp(final float[] a, final float[] bIn, final float t) {
      float bx = bIn[0];
      float by = bIn[1];
      float bz = bIn[2];
      float bw = bIn[3];
      final float ax = a[0];
      final float ay = a[1];
      final float az = a[2];
      final float aw = a[3];
      float dot = ax * bx + ay * by + az * bz + aw * bw;
      if (dot < 0f) {
        dot = -dot;
        bx = -bx;
        by = -by;
        bz = -bz;
        bw = -bw;
      }
      if (dot > 0.9995f) {
        return normalizeQuaternionArray(new float[] {
            ax + t * (bx - ax), ay + t * (by - ay), az + t * (bz - az), aw + t * (bw - aw)
        });
      }
      final float theta0 = (float) Math.acos(Math.max(-1f, Math.min(1f, dot)));
      final float theta = theta0 * t;
      final float sinTheta = (float) Math.sin(theta);
      final float sinTheta0 = (float) Math.sin(theta0);
      final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
      final float s1 = sinTheta / sinTheta0;
      return normalizeQuaternionArray(new float[] {
          s0 * ax + s1 * bx, s0 * ay + s1 * by, s0 * az + s1 * bz, s0 * aw + s1 * bw
      });
    }
  }

  private static final class TrackBuilder {
    private final int jointIndex;
    private final float[] translations;
    private final float[] rotations;
    private final float[] scales;

    private TrackBuilder(final int jointIndex, final int sampleCount, final BindTransform bind) {
      this.jointIndex = jointIndex;
      this.translations = new float[sampleCount * 3];
      this.rotations = new float[sampleCount * 4];
      this.scales = new float[sampleCount * 3];
      for (int i = 0; i < sampleCount; i++) {
        final int t = i * 3;
        this.translations[t] = bind.tx();
        this.translations[t + 1] = bind.ty();
        this.translations[t + 2] = bind.tz();
        this.scales[t] = bind.sx();
        this.scales[t + 1] = bind.sy();
        this.scales[t + 2] = bind.sz();
        final int r = i * 4;
        this.rotations[r] = bind.qx();
        this.rotations[r + 1] = bind.qy();
        this.rotations[r + 2] = bind.qz();
        this.rotations[r + 3] = bind.qw();
      }
    }

    private static TrackBuilder fromBind(final Joint joint, final int sampleCount) {
      return new TrackBuilder(joint.index(), sampleCount, joint.bind());
    }

    private void apply(final String path, final int sampleIndex, final float[] value) {
      switch (path) {
        case "translation" -> {
          final int b = sampleIndex * 3;
          this.translations[b] = value[0];
          this.translations[b + 1] = value[1];
          this.translations[b + 2] = value[2];
        }
        case "scale" -> {
          final int b = sampleIndex * 3;
          this.scales[b] = value[0];
          this.scales[b + 1] = value[1];
          this.scales[b + 2] = value[2];
        }
        case "rotation" -> {
          final int b = sampleIndex * 4;
          this.rotations[b] = value[0];
          this.rotations[b + 1] = value[1];
          this.rotations[b + 2] = value[2];
          this.rotations[b + 3] = value[3];
        }
        default -> throw new IllegalArgumentException("Unsupported channel path: " + path);
      }
    }
  }
}
