package org.animis.runtime.pose;

import org.animis.common.Validation;
import java.util.Arrays;

public final class PoseBuffer {
  private final int jointCount;
  private final float[] localTranslations;
  private final float[] localRotations;
  private final float[] localScales;

  public PoseBuffer(int jointCount) {
    Validation.requireJointCount(jointCount);
    this.jointCount = jointCount;
    this.localTranslations = new float[jointCount * 3];
    this.localRotations = new float[jointCount * 4];
    this.localScales = new float[jointCount * 3];
  }

  public int jointCount() {
    return this.jointCount;
  }

  public void setTranslation(int jointIndex, float x, float y, float z) {
    final int base = jointIndex * 3;
    this.localTranslations[base] = x;
    this.localTranslations[base + 1] = y;
    this.localTranslations[base + 2] = z;
  }

  public void setRotation(int jointIndex, float x, float y, float z, float w) {
    final int base = jointIndex * 4;
    this.localRotations[base] = x;
    this.localRotations[base + 1] = y;
    this.localRotations[base + 2] = z;
    this.localRotations[base + 3] = w;
  }

  public void setScale(int jointIndex, float x, float y, float z) {
    final int base = jointIndex * 3;
    this.localScales[base] = x;
    this.localScales[base + 1] = y;
    this.localScales[base + 2] = z;
  }

  public float[] localTranslations() {
    return this.localTranslations;
  }

  public float[] localRotations() {
    return this.localRotations;
  }

  public float[] localScales() {
    return this.localScales;
  }

  public void reset() {
    Arrays.fill(this.localTranslations, 0f);
    Arrays.fill(this.localRotations, 0f);
    Arrays.fill(this.localScales, 1f);
  }

  public Pose toPose() {
    return new Pose(this.localTranslations, this.localRotations, this.localScales, this.jointCount);
  }
}
