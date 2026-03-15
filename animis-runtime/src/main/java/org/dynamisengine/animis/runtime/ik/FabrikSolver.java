package org.dynamisengine.animis.runtime.ik;

import org.dynamisengine.animis.ik.FabrikChainDef;
import org.dynamisengine.animis.ik.IkChain;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.skeleton.Joint;
import org.dynamisengine.animis.skeleton.Skeleton;
import java.util.ArrayList;
import java.util.List;

public final class FabrikSolver implements IkSolver {
  private static final float EPSILON = 1e-6f;

  @Override
  public void solve(final PoseBuffer pose, final Skeleton skeleton, final IkChain chain, final IkTarget target) {
    final FabrikChainDef def = new FabrikChainDef(
        chain.name(),
        List.of(chain.rootJoint(), chain.midJoint(), chain.tipJoint()),
        1e-3f,
        15,
        List.of());
    solve(pose, skeleton, def, target);
  }

  public void solve(final PoseBuffer pose, final Skeleton skeleton, final FabrikChainDef chain, final IkTarget target) {
    if (pose.jointCount() < skeleton.joints().size()) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }
    final int jointCount = skeleton.joints().size();
    validateChain(skeleton, chain);

    final int n = chain.joints().size();
    if (n == 1) {
      moveSingleJointToTarget(pose, skeleton, chain.joints().getFirst(), target);
      return;
    }

    final float[] worldTx = new float[jointCount];
    final float[] worldTy = new float[jointCount];
    final float[] worldTz = new float[jointCount];
    final float[] worldRx = new float[jointCount];
    final float[] worldRy = new float[jointCount];
    final float[] worldRz = new float[jointCount];
    final float[] worldRw = new float[jointCount];
    computeWorldTransforms(skeleton, pose, worldTx, worldTy, worldTz, worldRx, worldRy, worldRz, worldRw);

    final Vec3[] positions = new Vec3[n];
    final float[] lengths = new float[n - 1];
    float totalLength = 0f;
    for (int i = 0; i < n; i++) {
      final int jointIndex = chain.joints().get(i);
      positions[i] = new Vec3(worldTx[jointIndex], worldTy[jointIndex], worldTz[jointIndex]);
      if (i > 0) {
        lengths[i - 1] = positions[i].sub(positions[i - 1]).length();
        totalLength += lengths[i - 1];
      }
    }
    if (totalLength <= EPSILON) {
      return;
    }

    final Vec3 rootOriginal = positions[0];
    final Vec3 targetPos = new Vec3(target.x(), target.y(), target.z());
    final float targetDist = targetPos.sub(rootOriginal).length();
    if (targetDist > totalLength) {
      final Vec3 dir = targetPos.sub(rootOriginal).normalized();
      for (int i = 1; i < n; i++) {
        positions[i] = positions[i - 1].add(dir.scale(lengths[i - 1]));
      }
    } else {
      iterateFabrik(positions, lengths, rootOriginal, targetPos, chain.tolerance(), chain.maxIterations(), chain.angleLimits());
    }

    writeSolvedChainToPose(pose, skeleton, chain.joints(), positions, worldTx, worldTy, worldTz, worldRx, worldRy, worldRz, worldRw);
  }

  private static void iterateFabrik(
      final Vec3[] positions,
      final float[] lengths,
      final Vec3 rootOriginal,
      final Vec3 targetPos,
      final float tolerance,
      final int maxIterations,
      final List<float[]> angleLimits) {
    final int n = positions.length;
    for (int iter = 0; iter < maxIterations; iter++) {
      positions[n - 1] = targetPos;
      for (int i = n - 2; i >= 0; i--) {
        final Vec3 dir = positions[i].sub(positions[i + 1]).normalized();
        positions[i] = positions[i + 1].add(dir.scale(lengths[i]));
      }

      positions[0] = rootOriginal;
      for (int i = 1; i < n; i++) {
        final Vec3 dir = positions[i].sub(positions[i - 1]).normalized();
        positions[i] = positions[i - 1].add(dir.scale(lengths[i - 1]));
      }

      applyAngleLimits(positions, lengths, angleLimits);

      final float tipDist = positions[n - 1].sub(targetPos).length();
      if (tipDist <= tolerance) {
        break;
      }
    }
  }

  private static void applyAngleLimits(final Vec3[] positions, final float[] lengths, final List<float[]> angleLimits) {
    if (angleLimits == null || angleLimits.isEmpty()) {
      return;
    }
    for (int i = 1; i < positions.length - 1; i++) {
      if (i >= angleLimits.size()) {
        break;
      }
      final float[] limits = angleLimits.get(i);
      if (limits == null) {
        continue;
      }
      final float minAngle = limits[0];
      final float maxAngle = limits[1];
      final Vec3 parent = positions[i - 1];
      final Vec3 curr = positions[i];
      final Vec3 child = positions[i + 1];
      final Vec3 toParent = parent.sub(curr).normalized();
      final Vec3 toChild = child.sub(curr).normalized();
      final float included = (float) Math.acos(clamp(toParent.dot(toChild), -1f, 1f));
      final float bend = (float) Math.PI - included;
      float clampedBend = bend;
      if (bend < minAngle) {
        clampedBend = minAngle;
      } else if (bend > maxAngle) {
        clampedBend = maxAngle;
      }
      if (Math.abs(clampedBend - bend) <= 1e-5f) {
        continue;
      }
      final Vec3 straightDir = toParent.scale(-1f);
      Vec3 axis = straightDir.cross(toChild).normalized();
      if (axis.length() <= EPSILON) {
        axis = orthogonal(toParent);
      }
      final Vec3 newDir = rotateAroundAxis(straightDir, axis, clampedBend).normalized();
      positions[i + 1] = curr.add(newDir.scale(lengths[i]));
    }
  }

  private static void writeSolvedChainToPose(
      final PoseBuffer pose,
      final Skeleton skeleton,
      final List<Integer> chain,
      final Vec3[] solved,
      final float[] worldTx,
      final float[] worldTy,
      final float[] worldTz,
      final float[] worldRx,
      final float[] worldRy,
      final float[] worldRz,
      final float[] worldRw) {
    final int n = chain.size();

    for (int i = 0; i < n; i++) {
      final int joint = chain.get(i);
      final Joint j = skeleton.joints().get(joint);
      final int parent = j.parentIndex();
      if (parent < 0) {
        pose.setTranslation(joint, solved[i].x, solved[i].y, solved[i].z);
        worldTx[joint] = solved[i].x;
        worldTy[joint] = solved[i].y;
        worldTz[joint] = solved[i].z;
        continue;
      }
      final Vec3 worldDelta = solved[i].sub(new Vec3(worldTx[parent], worldTy[parent], worldTz[parent]));
      final Quat parentWorld = new Quat(worldRx[parent], worldRy[parent], worldRz[parent], worldRw[parent]).normalized();
      final Vec3 local = parentWorld.inverse().rotate(worldDelta);
      pose.setTranslation(joint, local.x, local.y, local.z);
      worldTx[joint] = solved[i].x;
      worldTy[joint] = solved[i].y;
      worldTz[joint] = solved[i].z;
    }

    for (int i = 0; i < n - 1; i++) {
      final int joint = chain.get(i);
      final int child = chain.get(i + 1);
      final Vec3 desiredDir = solved[i + 1].sub(solved[i]).normalized();
      final Vec3 currentDir = new Vec3(
          worldTx[child] - worldTx[joint],
          worldTy[child] - worldTy[joint],
          worldTz[child] - worldTz[joint]).normalized();
      if (currentDir.length() <= EPSILON || desiredDir.length() <= EPSILON) {
        continue;
      }
      final Quat delta = Quat.fromTo(currentDir, desiredDir);
      final Quat oldWorld = new Quat(worldRx[joint], worldRy[joint], worldRz[joint], worldRw[joint]).normalized();
      final Quat newWorld = delta.mul(oldWorld).normalized();
      writeLocalRotation(pose, skeleton, joint, newWorld, worldRx, worldRy, worldRz, worldRw);
      worldRx[joint] = newWorld.x;
      worldRy[joint] = newWorld.y;
      worldRz[joint] = newWorld.z;
      worldRw[joint] = newWorld.w;
    }
  }

  private static void moveSingleJointToTarget(
      final PoseBuffer pose,
      final Skeleton skeleton,
      final int jointIndex,
      final IkTarget target) {
    final Joint joint = skeleton.joints().get(jointIndex);
    if (joint.parentIndex() >= 0) {
      return;
    }
    pose.setTranslation(jointIndex, target.x(), target.y(), target.z());
  }

  private static void validateChain(final Skeleton skeleton, final FabrikChainDef chain) {
    final int jointCount = skeleton.joints().size();
    for (final int joint : chain.joints()) {
      if (joint < 0 || joint >= jointCount) {
        throw new IllegalArgumentException("FABRIK joint out of range: " + joint);
      }
    }
    for (int i = 1; i < chain.joints().size(); i++) {
      final int parent = skeleton.joints().get(chain.joints().get(i)).parentIndex();
      if (parent != chain.joints().get(i - 1)) {
        throw new IllegalArgumentException("FABRIK chain must be contiguous parent->child order");
      }
    }
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
    final float[] localT = pose.localTranslations();
    final float[] localR = pose.localRotations();

    for (final Joint joint : skeleton.joints()) {
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

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static Vec3 rotateAroundAxis(final Vec3 v, final Vec3 axisIn, final float radians) {
    final Vec3 axis = axisIn.normalized();
    final float cos = (float) Math.cos(radians);
    final float sin = (float) Math.sin(radians);
    return v.scale(cos)
        .add(axis.cross(v).scale(sin))
        .add(axis.scale(axis.dot(v) * (1f - cos)));
  }

  private static Vec3 orthogonal(final Vec3 v) {
    if (Math.abs(v.x) < 0.9f) {
      return new Vec3(1f, 0f, 0f).cross(v).normalized();
    }
    return new Vec3(0f, 1f, 0f).cross(v).normalized();
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
