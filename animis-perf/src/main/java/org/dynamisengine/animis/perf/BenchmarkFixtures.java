package org.dynamisengine.animis.perf;

import org.dynamisengine.animis.blend.ClipNode;
import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.ClipId;
import org.dynamisengine.animis.clip.CurveTypeHint;
import org.dynamisengine.animis.clip.QuantizationSpec;
import org.dynamisengine.animis.clip.TrackMetadata;
import org.dynamisengine.animis.clip.TransformTrack;
import org.dynamisengine.animis.motion.MotionDatabase;
import org.dynamisengine.animis.motion.MotionFeatureSchema;
import org.dynamisengine.animis.motion.MotionFrame;
import org.dynamisengine.animis.runtime.api.DefaultAnimationRuntime;
import org.dynamisengine.animis.runtime.api.DefaultMotionMatcher;
import org.dynamisengine.animis.runtime.api.MotionMatcher;
import org.dynamisengine.animis.runtime.api.MotionQuery;
import org.dynamisengine.animis.runtime.pose.Pose;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.runtime.sampling.DefaultClipSampler;
import org.dynamisengine.animis.runtime.skinning.DefaultSkinningComputer;
import org.dynamisengine.animis.skeleton.BindTransform;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.Skeleton;
import org.dynamisengine.animis.state.StateDef;
import org.dynamisengine.animis.state.StateMachineDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class BenchmarkFixtures {
  private BenchmarkFixtures() {
  }

  static Skeleton chainSkeleton(final int jointCount) {
    final List<Joint> joints = new ArrayList<>(jointCount);
    for (int i = 0; i < jointCount; i++) {
      final int parent = i == 0 ? -1 : i - 1;
      final float tx = i == 0 ? 0f : 0.05f;
      joints.add(new Joint(i, "j" + i, parent, new BindTransform(tx, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)));
    }
    return new Skeleton("bench", joints, 0);
  }

  static Clip sampledClip(final String name, final int jointCount, final int sampleCount, final float durationSeconds) {
    final List<TransformTrack> tracks = new ArrayList<>(jointCount);
    final TrackMetadata metadata = new TrackMetadata(
        sampleCount <= 1 ? 1f : (sampleCount - 1) / durationSeconds,
        CurveTypeHint.SAMPLED,
        sampleCount,
        sampleCount <= 1 ? durationSeconds : durationSeconds / (sampleCount - 1),
        new QuantizationSpec(false, 0f, 0f, 0f));

    for (int joint = 0; joint < jointCount; joint++) {
      final float[] t = new float[sampleCount * 3];
      final float[] r = new float[sampleCount * 4];
      final float[] s = new float[sampleCount * 3];
      for (int frame = 0; frame < sampleCount; frame++) {
        final float phase = (float) frame / Math.max(1, sampleCount - 1);
        final int to = frame * 3;
        t[to] = phase * 0.05f * (joint + 1);
        t[to + 1] = 0.001f * joint;
        t[to + 2] = 0f;

        final int ro = frame * 4;
        final float angle = phase * 0.15f;
        final float half = angle * 0.5f;
        r[ro] = 0f;
        r[ro + 1] = (float) Math.sin(half);
        r[ro + 2] = 0f;
        r[ro + 3] = (float) Math.cos(half);

        s[to] = 1f;
        s[to + 1] = 1f;
        s[to + 2] = 1f;
      }
      tracks.add(new TransformTrack(joint, metadata, t, r, s));
    }
    final ClipId clipId = new ClipId(name);
    return new Clip(clipId, name, durationSeconds, tracks);
  }

  static Pose poseFromSkeleton(final Skeleton skeleton) {
    final int jointCount = skeleton.joints().size();
    final PoseBuffer buffer = new PoseBuffer(jointCount);
    for (Joint joint : skeleton.joints()) {
      final BindTransform bind = joint.bind();
      buffer.setTranslation(joint.index(), bind.tx(), bind.ty(), bind.tz());
      buffer.setRotation(joint.index(), bind.qx(), bind.qy(), bind.qz(), bind.qw());
      buffer.setScale(joint.index(), bind.sx(), bind.sy(), bind.sz());
    }
    return buffer.toPose();
  }

  static AnimatorRuntimeFixture animatorFixture(final int jointCount) {
    final Skeleton skeleton = chainSkeleton(jointCount);
    final Clip clip = sampledClip("idle", jointCount, 61, 2f);
    final ClipId clipId = clip.id();

    final StateMachineDef machine = new StateMachineDef(
        "bench-machine",
        List.of(new StateDef("idle", new ClipNode(clipId, 1f), List.of())),
        "idle");

    final Map<ClipId, Clip> clips = new HashMap<>();
    clips.put(clipId, clip);
    final Map<ClipId, Boolean> loops = new HashMap<>();
    loops.put(clipId, true);

    final DefaultAnimationRuntime runtime = new DefaultAnimationRuntime(
        clips,
        loops,
        List.of(),
        new DefaultSkinningComputer());

    return new AnimatorRuntimeFixture(runtime.create(machine, skeleton));
  }

  static MotionFixture motionFixture(final int frameCount, final int poseDims, final int trajDims, final int contactDims) {
    final Random random = new Random(42L);
    final List<MotionFrame> frames = new ArrayList<>(frameCount);
    for (int i = 0; i < frameCount; i++) {
      frames.add(new MotionFrame(
          i % 10,
          i * 0.033f,
          randomArray(random, poseDims),
          randomArray(random, trajDims),
          randomArray(random, contactDims)));
    }
    final MotionDatabase db = new MotionDatabase(
        List.of(),
        frames,
        new MotionFeatureSchema(List.of(0, 1, 2), 3, 0.1f));

    final MotionQuery query = new MotionQuery(
        randomArray(new Random(7L), poseDims),
        randomArray(new Random(8L), trajDims),
        randomArray(new Random(9L), contactDims));

    final MotionMatcher matcher = new DefaultMotionMatcher();
    return new MotionFixture(matcher, db, query);
  }

  static SamplingFixture samplingFixture(final int jointCount, final int sampleCount, final float durationSeconds) {
    final Skeleton skeleton = chainSkeleton(jointCount);
    final Clip clip = sampledClip("long", jointCount, sampleCount, durationSeconds);
    return new SamplingFixture(new DefaultClipSampler(), skeleton, clip, new PoseBuffer(jointCount));
  }

  static SkinningFixture skinningFixture(final int jointCount) {
    final Skeleton skeleton = chainSkeleton(jointCount);
    final Pose pose = poseFromSkeleton(skeleton);
    return new SkinningFixture(new DefaultSkinningComputer(), skeleton, pose);
  }

  private static float[] randomArray(final Random random, final int length) {
    final float[] data = new float[length];
    for (int i = 0; i < length; i++) {
      data[i] = random.nextFloat() * 2f - 1f;
    }
    return data;
  }

  record AnimatorRuntimeFixture(org.dynamisengine.animis.runtime.api.AnimatorInstance animator) {
  }

  record MotionFixture(MotionMatcher matcher, MotionDatabase db, MotionQuery query) {
  }

  record SamplingFixture(DefaultClipSampler sampler, Skeleton skeleton, Clip clip, PoseBuffer outPose) {
  }

  record SkinningFixture(DefaultSkinningComputer computer, Skeleton skeleton, Pose pose) {
  }
}
