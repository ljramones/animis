package org.animis.loader;

import org.animis.clip.Clip;
import org.animis.clip.ClipId;
import org.animis.clip.CurveTypeHint;
import org.animis.clip.TrackMetadata;
import org.animis.clip.TransformTrack;
import org.animis.skeleton.BindTransform;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BvhAnimationLoader implements AnimationLoader {
  @Override
  public AnimationLoadResult load(final Path path) throws IOException {
    try (InputStream stream = Files.newInputStream(path)) {
      return load(stream, "bvh");
    }
  }

  @Override
  public AnimationLoadResult load(final InputStream stream, final String formatHint) throws IOException {
    final String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    final Tokens tokens = new Tokens(text);
    tokens.expect("HIERARCHY");
    final ParseState state = new ParseState();
    parseJoint(tokens, state, -1, true);
    tokens.expect("MOTION");
    tokens.expect("Frames:");
    final int frameCount = Integer.parseInt(tokens.next());
    tokens.expect("Frame");
    tokens.expect("Time:");
    final float frameTime = Float.parseFloat(tokens.next());

    final int totalChannels = state.totalChannelCount;
    final float[][] frameValues = new float[frameCount][totalChannels];
    for (int f = 0; f < frameCount; f++) {
      for (int c = 0; c < totalChannels; c++) {
        frameValues[f][c] = Float.parseFloat(tokens.next());
      }
    }

    final List<Joint> joints = new ArrayList<>(state.joints.size());
    for (int i = 0; i < state.joints.size(); i++) {
      final JointDef def = state.joints.get(i);
      joints.add(new Joint(
          i,
          def.name,
          def.parentIndex,
          new BindTransform(def.offsetX, def.offsetY, def.offsetZ, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    }
    final Skeleton skeleton = new Skeleton("bvh-skeleton", joints, 0);

    final List<TransformTrack> tracks = new ArrayList<>(state.joints.size());
    for (int j = 0; j < state.joints.size(); j++) {
      final JointDef def = state.joints.get(j);
      final float[] translations = new float[frameCount * 3];
      final float[] rotations = new float[frameCount * 4];
      final float[] scales = new float[frameCount * 3];
      for (int f = 0; f < frameCount; f++) {
        float tx = def.offsetX;
        float ty = def.offsetY;
        float tz = def.offsetZ;
        final ArrayList<RotationChannel> rotationChannels = new ArrayList<>(3);
        for (int ci = 0; ci < def.channels.size(); ci++) {
          final Channel channel = def.channels.get(ci);
          final float value = frameValues[f][def.channelStart + ci];
          switch (channel) {
            case X_POSITION -> tx = value;
            case Y_POSITION -> ty = value;
            case Z_POSITION -> tz = value;
            case X_ROTATION, Y_ROTATION, Z_ROTATION -> rotationChannels.add(new RotationChannel(channel, value));
          }
        }
        final float[] q = quatFromEulerChannels(rotationChannels);
        final int tb = f * 3;
        translations[tb] = tx;
        translations[tb + 1] = ty;
        translations[tb + 2] = tz;
        scales[tb] = 1f;
        scales[tb + 1] = 1f;
        scales[tb + 2] = 1f;
        final int rb = f * 4;
        rotations[rb] = q[0];
        rotations[rb + 1] = q[1];
        rotations[rb + 2] = q[2];
        rotations[rb + 3] = q[3];
      }
      final TrackMetadata metadata = new TrackMetadata(
          frameTime > 0f ? 1f / frameTime : 0f,
          CurveTypeHint.SAMPLED,
          frameCount,
          frameTime,
          null);
      tracks.add(new TransformTrack(j, metadata, translations, rotations, scales));
    }

    final float duration = frameTime * Math.max(0, frameCount - 1);
    final Clip clip = new Clip(new ClipId("bvh_clip"), "bvh_clip", duration, tracks);
    return new AnimationLoadResult(List.of(skeleton), List.of(clip), Map.of(0, 0));
  }

  private static float[] quatFromEulerChannels(final List<RotationChannel> channels) {
    float[] q = new float[] {0f, 0f, 0f, 1f};
    for (final RotationChannel channel : channels) {
      final float rad = (float) Math.toRadians(channel.degrees);
      final float[] axisQ = switch (channel.kind) {
        case X_ROTATION -> axisAngle(1f, 0f, 0f, rad);
        case Y_ROTATION -> axisAngle(0f, 1f, 0f, rad);
        case Z_ROTATION -> axisAngle(0f, 0f, 1f, rad);
        default -> throw new IllegalStateException("Unexpected rotation channel");
      };
      q = mulQuat(q, axisQ);
    }
    return normalize(q);
  }

  private static float[] axisAngle(final float ax, final float ay, final float az, final float angle) {
    final float half = angle * 0.5f;
    final float s = (float) Math.sin(half);
    return new float[] {ax * s, ay * s, az * s, (float) Math.cos(half)};
  }

  private static float[] mulQuat(final float[] a, final float[] b) {
    return normalize(new float[] {
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    });
  }

  private static float[] normalize(final float[] q) {
    final float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
    if (len <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    return new float[] {q[0] / len, q[1] / len, q[2] / len, q[3] / len};
  }

  private static void parseJoint(
      final Tokens tokens,
      final ParseState state,
      final int parentIndex,
      final boolean root) {
    final String kind = tokens.next();
    final boolean endSite = kind.equals("End");
    final String name;
    if (root) {
      if (!kind.equals("ROOT")) {
        throw new IllegalArgumentException("Expected ROOT in BVH");
      }
      name = tokens.next();
    } else if (endSite) {
      tokens.expect("Site");
      name = "EndSite";
    } else {
      if (!kind.equals("JOINT")) {
        throw new IllegalArgumentException("Expected JOINT or End Site");
      }
      name = tokens.next();
    }

    tokens.expect("{");
    tokens.expect("OFFSET");
    final float ox = Float.parseFloat(tokens.next());
    final float oy = Float.parseFloat(tokens.next());
    final float oz = Float.parseFloat(tokens.next());
    if (endSite) {
      tokens.expect("}");
      return;
    }

    tokens.expect("CHANNELS");
    final int channelCount = Integer.parseInt(tokens.next());
    final ArrayList<Channel> channels = new ArrayList<>(channelCount);
    for (int i = 0; i < channelCount; i++) {
      channels.add(Channel.fromToken(tokens.next()));
    }

    final int jointIndex = state.joints.size();
    state.joints.add(new JointDef(name, parentIndex, ox, oy, oz, state.totalChannelCount, channels));
    state.totalChannelCount += channelCount;

    while (true) {
      final String peek = tokens.peek();
      if (peek.equals("}")) {
        tokens.next();
        break;
      }
      parseJoint(tokens, state, jointIndex, false);
    }
  }

  private enum Channel {
    X_POSITION,
    Y_POSITION,
    Z_POSITION,
    X_ROTATION,
    Y_ROTATION,
    Z_ROTATION;

    private static Channel fromToken(final String token) {
      return switch (token) {
        case "Xposition" -> X_POSITION;
        case "Yposition" -> Y_POSITION;
        case "Zposition" -> Z_POSITION;
        case "Xrotation" -> X_ROTATION;
        case "Yrotation" -> Y_ROTATION;
        case "Zrotation" -> Z_ROTATION;
        default -> throw new IllegalArgumentException("Unsupported BVH channel: " + token);
      };
    }
  }

  private record RotationChannel(Channel kind, float degrees) {}

  private record JointDef(
      String name,
      int parentIndex,
      float offsetX,
      float offsetY,
      float offsetZ,
      int channelStart,
      List<Channel> channels
  ) {}

  private static final class ParseState {
    private final ArrayList<JointDef> joints = new ArrayList<>();
    private int totalChannelCount = 0;
  }

  private static final class Tokens {
    private final String[] tokens;
    private int cursor;

    private Tokens(final String text) {
      final String prepared = text
          .replace("{", " { ")
          .replace("}", " } ")
          .replace("\r", " ")
          .replace("\n", " ");
      this.tokens = prepared.trim().split("\\s+");
      this.cursor = 0;
    }

    private String peek() {
      if (cursor >= tokens.length) {
        throw new IllegalArgumentException("Unexpected end of BVH input");
      }
      return tokens[cursor];
    }

    private String next() {
      final String token = peek();
      cursor++;
      return token;
    }

    private void expect(final String expected) {
      final String token = next();
      if (!token.equals(expected)) {
        throw new IllegalArgumentException("Expected '" + expected + "' but found '" + token + "'");
      }
    }
  }
}
