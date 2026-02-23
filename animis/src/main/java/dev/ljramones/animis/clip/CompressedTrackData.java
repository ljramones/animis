package dev.ljramones.animis.clip;

import java.util.Arrays;

public record CompressedTrackData(
    float baseTx,
    float baseTy,
    float baseTz,
    float baseSx,
    float baseSy,
    float baseSz,
    short[] translationDeltas,
    short[] scaleDeltas,
    short[] rotationSmallestThree,
    byte[] rotationMeta
) {
  public CompressedTrackData {
    translationDeltas = translationDeltas == null ? new short[0] : Arrays.copyOf(translationDeltas, translationDeltas.length);
    scaleDeltas = scaleDeltas == null ? new short[0] : Arrays.copyOf(scaleDeltas, scaleDeltas.length);
    rotationSmallestThree = rotationSmallestThree == null ? new short[0] : Arrays.copyOf(rotationSmallestThree, rotationSmallestThree.length);
    rotationMeta = rotationMeta == null ? new byte[0] : Arrays.copyOf(rotationMeta, rotationMeta.length);
  }
}
