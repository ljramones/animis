package org.dynamisengine.animis.runtime.ik;

import org.dynamisengine.animis.ik.IkChain;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.Skeleton;
import java.util.List;

public final class TwoBoneIkSolver implements IkSolver {
  private static final float EPSILON = 1e-6f;

  @Override
  public void solve(final PoseBuffer pose, final Skeleton skeleton, final IkChain chain, final IkTarget target) {
    final int jointCount = skeleton.joints().size();
    validateChain(chain, jointCount);
    if (pose.jointCount() < jointCount) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }

    final float[] worldTx = new float[jointCount];
    final float[] worldTy = new float[jointCount];
    final float[] worldTz = new float[jointCount];
    final float[] worldRx = new float[jointCount];
    final float[] worldRy = new float[jointCount];
    final float[] worldRz = new float[jointCount];
    final float[] worldRw = new float[jointCount];
    computeWorldTransforms(skeleton, pose, worldTx, worldTy, worldTz, worldRx, worldRy, worldRz, worldRw);

    final int root = chain.rootJoint();
    final int mid = chain.midJoint();
    final int tip = chain.tipJoint();

    final Vec3 rootPos = new Vec3(worldTx[root], worldTy[root], worldTz[root]);
    final Vec3 midPos = new Vec3(worldTx[mid], worldTy[mid], worldTz[mid]);
    final Vec3 tipPos = new Vec3(worldTx[tip], worldTy[tip], worldTz[tip]);

    final float upperLen = midPos.sub(rootPos).length();
    final float lowerLen = tipPos.sub(midPos).length();
    if (upperLen <= EPSILON || lowerLen <= EPSILON) {
      return;
    }

    final Vec3 targetPos = new Vec3(target.x(), target.y(), target.z());
    final Vec3 targetFromRoot = targetPos.sub(rootPos);
    final float totalLen = upperLen + lowerLen;
    if (totalLen <= EPSILON) {
      return;
    }

    final float minDist = Math.max(0f, chain.minStretch()) * totalLen;
    final float maxDist = Math.max(minDist, chain.maxStretch() * totalLen);
    float desiredDist = clamp(targetFromRoot.length(), minDist, maxDist);
    desiredDist = clamp(desiredDist, Math.abs(upperLen - lowerLen) + EPSILON, totalLen);

    Vec3 toTargetDir = targetFromRoot.normalized();
    if (toTargetDir.isNearZero()) {
      toTargetDir = midPos.sub(rootPos).normalized();
      if (toTargetDir.isNearZero()) {
        toTargetDir = new Vec3(1f, 0f, 0f);
      }
    }

    final Vec3 poleDir = resolvePoleDirection(target, chain, skeleton, worldTx, worldTy, worldTz, rootPos, midPos, tipPos);
    Vec3 planeNormal = toTargetDir.cross(poleDir).normalized();
    if (planeNormal.isNearZero()) {
      planeNormal = midPos.sub(rootPos).cross(tipPos.sub(midPos)).normalized();
    }
    if (planeNormal.isNearZero()) {
      planeNormal = orthogonal(toTargetDir);
    }
    Vec3 bendDir = planeNormal.cross(toTargetDir).normalized();
    if (bendDir.isNearZero()) {
      bendDir = orthogonal(toTargetDir);
    }

    final float cosRoot = clamp(
        (desiredDist * desiredDist + upperLen * upperLen - lowerLen * lowerLen) / (2f * desiredDist * upperLen),
        -1f,
        1f);
    final float sinRoot = (float) Math.sqrt(Math.max(0f, 1f - cosRoot * cosRoot));

    final Vec3 desiredMid = rootPos.add(toTargetDir.scale(upperLen * cosRoot)).add(bendDir.scale(upperLen * sinRoot));
    final Vec3 desiredTip = rootPos.add(toTargetDir.scale(desiredDist));

    final Vec3 currentUpperDir = midPos.sub(rootPos).normalized();
    final Vec3 desiredUpperDir = desiredMid.sub(rootPos).normalized();
    final Quat rootDelta = Quat.fromTo(currentUpperDir, desiredUpperDir);

    final Vec3 currentLowerDir = tipPos.sub(midPos).normalized();
    final Vec3 lowerAfterRoot = rootDelta.rotate(currentLowerDir).normalized();
    final Vec3 desiredLowerDir = desiredTip.sub(desiredMid).normalized();
    final Quat midDelta = Quat.fromTo(lowerAfterRoot, desiredLowerDir);

    final Quat rootWorldOld = new Quat(worldRx[root], worldRy[root], worldRz[root], worldRw[root]).normalized();
    final Quat rootWorldNew = rootDelta.mul(rootWorldOld).normalized();

    final Quat midWorldOld = new Quat(worldRx[mid], worldRy[mid], worldRz[mid], worldRw[mid]).normalized();
    final Quat midWorldAfterRoot = rootDelta.mul(midWorldOld).normalized();
    final Quat midWorldNew = midDelta.mul(midWorldAfterRoot).normalized();

    writeLocalRotation(pose, skeleton, root, rootWorldNew, worldRx, worldRy, worldRz, worldRw);
    worldRx[root] = rootWorldNew.x;
    worldRy[root] = rootWorldNew.y;
    worldRz[root] = rootWorldNew.z;
    worldRw[root] = rootWorldNew.w;
    writeLocalRotation(pose, skeleton, mid, midWorldNew, worldRx, worldRy, worldRz, worldRw);
  }

  private static void validateChain(final IkChain chain, final int jointCount) {
    if (chain.rootJoint() < 0 || chain.rootJoint() >= jointCount) {
      throw new IllegalArgumentException("IK root joint out of range");
    }
    if (chain.midJoint() < 0 || chain.midJoint() >= jointCount) {
      throw new IllegalArgumentException("IK mid joint out of range");
    }
    if (chain.tipJoint() < 0 || chain.tipJoint() >= jointCount) {
      throw new IllegalArgumentException("IK tip joint out of range");
    }
  }

  private static Vec3 resolvePoleDirection(
      final IkTarget target,
      final IkChain chain,
      final Skeleton skeleton,
      final float[] worldTx,
      final float[] worldTy,
      final float[] worldTz,
      final Vec3 rootPos,
      final Vec3 midPos,
      final Vec3 tipPos) {
    if (target.hasPole()) {
      return new Vec3(target.poleX(), target.poleY(), target.poleZ()).sub(rootPos).normalized();
    }
    if (chain.poleTargetJoint().isPresent()) {
      final int poleIndex = chain.poleTargetJoint().get();
      if (poleIndex >= 0 && poleIndex < skeleton.joints().size()) {
        return new Vec3(worldTx[poleIndex], worldTy[poleIndex], worldTz[poleIndex]).sub(rootPos).normalized();
      }
    }
    final Vec3 normal = midPos.sub(rootPos).cross(tipPos.sub(midPos)).normalized();
    if (!normal.isNearZero()) {
      return normal.cross(tipPos.sub(rootPos)).normalized();
    }
    return new Vec3(0f, 1f, 0f);
  }

  private static void computeWorldTransforms(
      final Skeleton skeleton,
      final PoseBuffer pose,
      final float[] worldTx,
      final float[] worldTy,
      final float[] worldTz,
      final float[] worldRx,
      final float[] worldRy,
      final float[] worldRz,
      final float[] worldRw) {
    final List<Joint> joints = skeleton.joints();
    final float[] localT = pose.localTranslations();
    final float[] localR = pose.localRotations();

    for (final Joint joint : joints) {
      final int i = joint.index();
      final int tBase = i * 3;
      final int rBase = i * 4;
      final Vec3 lt = new Vec3(localT[tBase], localT[tBase + 1], localT[tBase + 2]);
      final Quat lr = new Quat(localR[rBase], localR[rBase + 1], localR[rBase + 2], localR[rBase + 3]).normalized();

      if (joint.parentIndex() < 0) {
        worldTx[i] = lt.x;
        worldTy[i] = lt.y;
        worldTz[i] = lt.z;
        worldRx[i] = lr.x;
        worldRy[i] = lr.y;
        worldRz[i] = lr.z;
        worldRw[i] = lr.w;
        continue;
      }

      final int p = joint.parentIndex();
      final Quat pr = new Quat(worldRx[p], worldRy[p], worldRz[p], worldRw[p]).normalized();
      final Vec3 pt = new Vec3(worldTx[p], worldTy[p], worldTz[p]);
      final Vec3 wt = pt.add(pr.rotate(lt));
      final Quat wr = pr.mul(lr).normalized();
      worldTx[i] = wt.x;
      worldTy[i] = wt.y;
      worldTz[i] = wt.z;
      worldRx[i] = wr.x;
      worldRy[i] = wr.y;
      worldRz[i] = wr.z;
      worldRw[i] = wr.w;
    }
  }

  private static void writeLocalRotation(
      final PoseBuffer pose,
      final Skeleton skeleton,
      final int jointIndex,
      final Quat worldRotation,
      final float[] worldRx,
      final float[] worldRy,
      final float[] worldRz,
      final float[] worldRw) {
    final Joint joint = skeleton.joints().get(jointIndex);
    Quat local = worldRotation;
    if (joint.parentIndex() >= 0) {
      final int parent = joint.parentIndex();
      final Quat parentWorld = new Quat(worldRx[parent], worldRy[parent], worldRz[parent], worldRw[parent]).normalized();
      local = parentWorld.inverse().mul(worldRotation).normalized();
    }
    pose.setRotation(jointIndex, local.x, local.y, local.z, local.w);
  }

  private static Vec3 orthogonal(final Vec3 v) {
    if (Math.abs(v.x) < 0.9f) {
      return new Vec3(1f, 0f, 0f).cross(v).normalized();
    }
    return new Vec3(0f, 1f, 0f).cross(v).normalized();
  }

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static final class Vec3 {
    private final float x;
    private final float y;
    private final float z;

    private Vec3(final float x, final float y, final float z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }

    private Vec3 add(final Vec3 o) {
      return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z);
    }

    private Vec3 sub(final Vec3 o) {
      return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z);
    }

    private Vec3 scale(final float s) {
      return new Vec3(this.x * s, this.y * s, this.z * s);
    }

    private float dot(final Vec3 o) {
      return this.x * o.x + this.y * o.y + this.z * o.z;
    }

    private Vec3 cross(final Vec3 o) {
      return new Vec3(
          this.y * o.z - this.z * o.y,
          this.z * o.x - this.x * o.z,
          this.x * o.y - this.y * o.x);
    }

    private float length() {
      return (float) Math.sqrt(this.dot(this));
    }

    private Vec3 normalized() {
      final float len = this.length();
      if (len <= EPSILON) {
        return new Vec3(0f, 0f, 0f);
      }
      final float inv = 1f / len;
      return new Vec3(this.x * inv, this.y * inv, this.z * inv);
    }

    private boolean isNearZero() {
      return this.length() <= EPSILON;
    }
  }

  private static final class Quat {
    private final float x;
    private final float y;
    private final float z;
    private final float w;

    private Quat(final float x, final float y, final float z, final float w) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.w = w;
    }

    private Quat normalized() {
      final float lenSq = this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w;
      if (lenSq <= EPSILON) {
        return new Quat(0f, 0f, 0f, 1f);
      }
      final float inv = 1f / (float) Math.sqrt(lenSq);
      return new Quat(this.x * inv, this.y * inv, this.z * inv, this.w * inv);
    }

    private Quat inverse() {
      return new Quat(-this.x, -this.y, -this.z, this.w).normalized();
    }

    private Quat mul(final Quat o) {
      return new Quat(
          this.w * o.x + this.x * o.w + this.y * o.z - this.z * o.y,
          this.w * o.y - this.x * o.z + this.y * o.w + this.z * o.x,
          this.w * o.z + this.x * o.y - this.y * o.x + this.z * o.w,
          this.w * o.w - this.x * o.x - this.y * o.y - this.z * o.z);
    }

    private Vec3 rotate(final Vec3 v) {
      final Quat qv = new Quat(v.x, v.y, v.z, 0f);
      final Quat out = this.mul(qv).mul(this.inverse());
      return new Vec3(out.x, out.y, out.z);
    }

    private static Quat fromTo(final Vec3 fromIn, final Vec3 toIn) {
      final Vec3 from = fromIn.normalized();
      final Vec3 to = toIn.normalized();
      final float dot = from.dot(to);
      if (dot > 1f - EPSILON) {
        return new Quat(0f, 0f, 0f, 1f);
      }
      if (dot < -1f + EPSILON) {
        final Vec3 axis = orthogonal(from);
        return axisAngle(axis, (float) Math.PI);
      }
      final Vec3 cross = from.cross(to);
      return new Quat(cross.x, cross.y, cross.z, 1f + dot).normalized();
    }

    private static Quat axisAngle(final Vec3 axisIn, final float radians) {
      final Vec3 axis = axisIn.normalized();
      final float half = radians * 0.5f;
      final float s = (float) Math.sin(half);
      return new Quat(axis.x * s, axis.y * s, axis.z * s, (float) Math.cos(half)).normalized();
    }
  }
}
