package org.animis.loader;

import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.meshforge.loader.gltf.read.MiniJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.animis.loader.GltfAccessorReader.*;
import static org.animis.loader.GltfJsonHelper.*;

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
    final String jsonStr = sanitizeJsonChunk(jsonChunk);
    return loadGltfJson(jsonStr, baseDir, binChunk);
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
    final Matrix4f[] worldBind = inverseBind == null ? null : invertAllMat4(inverseBind, jointNodes.size());

    final ArrayList<Joint> joints = new ArrayList<>(jointNodes.size());
    final Matrix4f tempInv = new Matrix4f();
    final Matrix4f tempLocal = new Matrix4f();
    for (int i = 0; i < jointNodes.size(); i++) {
      final int nodeIndex = jointNodes.get(i);
      final int parentNode = parentNodeMap.getOrDefault(nodeIndex, -1);
      final int parentJoint = parentNode < 0 ? -1 : nodeToJoint.getOrDefault(parentNode, -1);

      final BindTransform bind;
      if (worldBind != null) {
        if (parentJoint < 0) {
          bind = decomposeBind(worldBind[i]);
        } else {
          worldBind[parentJoint].invert(tempInv);
          tempInv.mul(worldBind[i], tempLocal);
          bind = decomposeBind(tempLocal);
        }
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

  private static BindTransform decomposeBind(final Matrix4f m) {
    final Vector3f translation = new Vector3f();
    final Vector3f scale = new Vector3f();
    final Quaternionf rotation = new Quaternionf();
    m.getTranslation(translation);
    m.getScale(scale);
    m.getNormalizedRotation(rotation);
    return new BindTransform(
        translation.x, translation.y, translation.z,
        rotation.x, rotation.y, rotation.z, rotation.w,
        scale.x, scale.y, scale.z);
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
      if (input.components() != 1) {
        throw new IllegalArgumentException("Sampler input accessor must be SCALAR");
      }
      final int expectedComponents = path.equals("rotation") ? 4 : 3;
      if (output.components() != expectedComponents) {
        throw new IllegalArgumentException("Accessor component count mismatch for path " + path);
      }
      if (output.count() != input.count()) {
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
    final Accessor acc = accessor(accessors, views, number.intValue());
    if (acc.components() != 16) {
      throw new IllegalArgumentException("inverseBindMatrices accessor must be MAT4");
    }
    if (acc.count() != expectedJoints) {
      throw new IllegalArgumentException("inverseBindMatrices count does not match joint count");
    }
    return readFloatAccessorData(acc, buffers);
  }

  private static Matrix4f[] invertAllMat4(final float[] mats, final int count) {
    final Matrix4f src = new Matrix4f();
    final Matrix4f[] out = new Matrix4f[count];
    for (int i = 0; i < count; i++) {
      final float[] slice = new float[16];
      System.arraycopy(mats, i * 16, slice, 0, 16);
      src.set(slice);
      out[i] = new Matrix4f();
      src.invert(out[i]);
    }
    return out;
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
    final Quaternionf q = new Quaternionf();
    final float[] r = floatArray(node.get("rotation"), 4, new float[] {0f, 0f, 0f, 1f});
    q.set(r[0], r[1], r[2], r[3]).normalize();
    final float[] s = floatArray(node.get("scale"), 3, new float[] {1f, 1f, 1f});
    return new BindTransform(t[0], t[1], t[2], q.x, q.y, q.z, q.w, s[0], s[1], s[2]);
  }

  private static float[] normalizeQuaternionArray(final float[] q) {
    final Quaternionf temp = new Quaternionf();
    for (int i = 0; i < q.length; i += 4) {
      temp.set(q[i], q[i + 1], q[i + 2], q[i + 3]).normalize();
      q[i] = temp.x;
      q[i + 1] = temp.y;
      q[i + 2] = temp.z;
      q[i + 3] = temp.w;
    }
    return q;
  }

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
        return slerpArray(a, b, alpha);
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

    private static float[] slerpArray(final float[] a, final float[] b, final float t) {
      final Quaternionf qa = new Quaternionf(a[0], a[1], a[2], a[3]);
      final Quaternionf qb = new Quaternionf(b[0], b[1], b[2], b[3]);
      final Quaternionf result = new Quaternionf();
      qa.slerp(qb, t, result).normalize(result);
      return new float[] {result.x, result.y, result.z, result.w};
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
