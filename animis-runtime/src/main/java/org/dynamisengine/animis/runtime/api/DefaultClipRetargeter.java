package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.clip.CompressedTrackData;
import org.dynamisengine.animis.clip.TransformTrack;
import org.dynamisengine.animis.retarget.JointMapping;
import org.dynamisengine.animis.retarget.RetargetMap;
import org.dynamisengine.animis.skeleton.BindTransform;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.Skeleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultClipRetargeter implements ClipRetargeter {
  @Override
  public Clip retarget(
      final Clip source,
      final Skeleton sourceSkeleton,
      final Skeleton targetSkeleton,
      final RetargetMap map) {
    final Map<String, Integer> sourceByName = indexByName(sourceSkeleton);
    final Map<String, Integer> targetByName = indexByName(targetSkeleton);
    final Map<Integer, TransformTrack> sourceTracks = tracksByJoint(source);

    final ArrayList<TransformTrack> outTracks = new ArrayList<>();
    for (final JointMapping mapping : map.mappings()) {
      final Integer srcIndex = sourceByName.get(mapping.sourceName());
      final Integer dstIndex = targetByName.get(mapping.targetName());
      if (srcIndex == null || dstIndex == null) {
        continue;
      }
      final TransformTrack srcTrack = sourceTracks.get(srcIndex);
      if (srcTrack == null) {
        continue;
      }
      outTracks.add(remapTrack(srcTrack, srcIndex, dstIndex, sourceSkeleton, targetSkeleton, mapping, map.scaleTranslations()));
    }

    final Optional<org.dynamisengine.animis.clip.RootMotionDef> rootMotion = source.rootMotion().flatMap(def -> {
      final Joint root = sourceSkeleton.joints().get(def.rootJoint());
      final Integer mappedRoot = targetByName.get(root.name());
      if (mappedRoot == null) {
        return Optional.empty();
      }
      return Optional.of(new org.dynamisengine.animis.clip.RootMotionDef(
          mappedRoot,
          def.extractTranslationXZ(),
          def.extractTranslationY(),
          def.extractRotationY()));
    });

    return new Clip(
        source.id(),
        source.name(),
        source.durationSeconds(),
        outTracks,
        rootMotion,
        source.events());
  }

  private static TransformTrack remapTrack(
      final TransformTrack sourceTrack,
      final int sourceJoint,
      final int targetJoint,
      final Skeleton sourceSkeleton,
      final Skeleton targetSkeleton,
      final JointMapping mapping,
      final boolean scaleTranslations) {
    final int sampleCount = sampleCount(sourceTrack);
    final float[] t = new float[sampleCount * 3];
    final float[] r = new float[sampleCount * 4];
    final float[] s = new float[sampleCount * 3];

    final float ratio = scaleTranslations ? limbRatio(sourceJoint, targetJoint, sourceSkeleton, targetSkeleton) : 1f;
    final float[] offset = mapping.rotationOffset().orElse(null);

    for (int i = 0; i < sampleCount; i++) {
      final int tb = i * 3;
      final int rb = i * 4;

      final float tx = readTranslation(sourceTrack, i, 0);
      final float ty = readTranslation(sourceTrack, i, 1);
      final float tz = readTranslation(sourceTrack, i, 2);
      t[tb] = tx * ratio;
      t[tb + 1] = ty * ratio;
      t[tb + 2] = tz * ratio;

      final float[] q = readRotation(sourceTrack, i);
      final float[] qOut;
      if (offset != null && offset.length == 4) {
        qOut = normalize(mul(offset, q));
      } else {
        qOut = q;
      }
      r[rb] = qOut[0];
      r[rb + 1] = qOut[1];
      r[rb + 2] = qOut[2];
      r[rb + 3] = qOut[3];

      s[tb] = readScale(sourceTrack, i, 0);
      s[tb + 1] = readScale(sourceTrack, i, 1);
      s[tb + 2] = readScale(sourceTrack, i, 2);
    }

    return new TransformTrack(targetJoint, sourceTrack.metadata(), t, r, s, Optional.empty());
  }

  private static float limbRatio(
      final int sourceJoint,
      final int targetJoint,
      final Skeleton sourceSkeleton,
      final Skeleton targetSkeleton) {
    final BindTransform sb = sourceSkeleton.joints().get(sourceJoint).bind();
    final BindTransform tb = targetSkeleton.joints().get(targetJoint).bind();
    final float sl = length(sb.tx(), sb.ty(), sb.tz());
    final float tl = length(tb.tx(), tb.ty(), tb.tz());
    if (sl <= 1e-6f || tl <= 1e-6f) {
      return 1f;
    }
    return tl / sl;
  }

  private static float length(final float x, final float y, final float z) {
    return (float) Math.sqrt(x * x + y * y + z * z);
  }

  private static Map<String, Integer> indexByName(final Skeleton skeleton) {
    final HashMap<String, Integer> byName = new HashMap<>();
    for (final Joint joint : skeleton.joints()) {
      byName.put(joint.name(), joint.index());
    }
    return byName;
  }

  private static Map<Integer, TransformTrack> tracksByJoint(final Clip clip) {
    final HashMap<Integer, TransformTrack> byJoint = new HashMap<>();
    for (final TransformTrack track : clip.tracks()) {
      byJoint.put(track.jointIndex(), track);
    }
    return byJoint;
  }

  private static int sampleCount(final TransformTrack track) {
    if (track.metadata().sampleCount() > 0) {
      return track.metadata().sampleCount();
    }
    if (track.compressed().isPresent()) {
      return track.compressed().get().rotationMeta().length;
    }
    return Math.min(track.translations().length / 3, Math.min(track.rotations().length / 4, track.scales().length / 3));
  }

  private static float readTranslation(final TransformTrack track, final int sampleIndex, final int axis) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      final float step = track.metadata().quantization().posStep() > 0f ? track.metadata().quantization().posStep() : 1e-4f;
      final int idx = sampleIndex * 3 + axis;
      final short q = idx < c.translationDeltas().length ? c.translationDeltas()[idx] : 0;
      final float base = axis == 0 ? c.baseTx() : axis == 1 ? c.baseTy() : c.baseTz();
      return base + q * step;
    }
    return read3(track.translations(), sampleIndex, axis, 0f);
  }

  private static float readScale(final TransformTrack track, final int sampleIndex, final int axis) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      final float step = track.metadata().quantization().scaleStep() > 0f ? track.metadata().quantization().scaleStep() : 1e-4f;
      final int idx = sampleIndex * 3 + axis;
      final short q = idx < c.scaleDeltas().length ? c.scaleDeltas()[idx] : 0;
      final float base = axis == 0 ? c.baseSx() : axis == 1 ? c.baseSy() : c.baseSz();
      return base + q * step;
    }
    return read3(track.scales(), sampleIndex, axis, 1f);
  }

  private static float[] readRotation(final TransformTrack track, final int sampleIndex) {
    if (track.compressed().isPresent() && track.metadata().quantization() != null && track.metadata().quantization().enabled()) {
      final CompressedTrackData c = track.compressed().get();
      final int meta = c.rotationMeta()[sampleIndex] & 0xFF;
      final int largest = meta & 0x3;
      final int sign = ((meta >> 2) & 0x1) == 1 ? 1 : -1;
      final int rb = sampleIndex * 3;
      final float[] kept = new float[] {
          dequantizeUnit(c.rotationSmallestThree()[rb]),
          dequantizeUnit(c.rotationSmallestThree()[rb + 1]),
          dequantizeUnit(c.rotationSmallestThree()[rb + 2])
      };
      final float[] q = new float[4];
      int k = 0;
      float sumSq = 0f;
      for (int i = 0; i < 4; i++) {
        if (i == largest) {
          continue;
        }
        q[i] = kept[k++];
        sumSq += q[i] * q[i];
      }
      q[largest] = sign * (float) Math.sqrt(Math.max(0f, 1f - sumSq));
      return normalize(q);
    }
    return normalize(new float[] {
        read4(track.rotations(), sampleIndex, 0),
        read4(track.rotations(), sampleIndex, 1),
        read4(track.rotations(), sampleIndex, 2),
        read4(track.rotations(), sampleIndex, 3)
    });
  }

  private static float read3(final float[] data, final int sampleIndex, final int axis, final float defaultValue) {
    final int idx = sampleIndex * 3 + axis;
    return idx < data.length ? data[idx] : defaultValue;
  }

  private static float read4(final float[] data, final int sampleIndex, final int axis) {
    final int idx = sampleIndex * 4 + axis;
    if (idx < data.length) {
      return data[idx];
    }
    return axis == 3 ? 1f : 0f;
  }

  private static float dequantizeUnit(final short q) {
    return Math.max(-1f, Math.min(1f, q / 32767f));
  }

  private static float[] mul(final float[] a, final float[] b) {
    return new float[] {
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    };
  }

  private static float[] normalize(final float[] q) {
    final float lenSq = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3];
    if (lenSq <= 1e-8f) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = 1f / (float) Math.sqrt(lenSq);
    return new float[] {q[0] * inv, q[1] * inv, q[2] * inv, q[3] * inv};
  }
}
