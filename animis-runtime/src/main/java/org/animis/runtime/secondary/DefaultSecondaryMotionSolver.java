package org.animis.runtime.secondary;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.SecondaryChainDef;
import org.animis.skeleton.Skeleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSecondaryMotionSolver implements SecondaryMotionSolver {
  private static final float EPSILON = 1.0e-6f;
  private final Map<String, ChainState> states = new HashMap<>();

  @Override
  public void solve(final PoseBuffer pose, final Skeleton skeleton, final float dt) {
    if (dt <= 0f || skeleton.secondaryChains().isEmpty()) {
      return;
    }
    final float[] rotations = pose.localRotations();
    for (final SecondaryChainDef chain : skeleton.secondaryChains()) {
      final List<Integer> joints = chain.joints();
      final ChainState state = states.computeIfAbsent(chain.name(), ignored -> new ChainState(joints.size()));
      state.ensureSize(joints.size());
      solveChain(chain, joints, rotations, dt, state);
    }
  }

  private static void solveChain(
      final SecondaryChainDef chain,
      final List<Integer> joints,
      final float[] rotations,
      final float dt,
      final ChainState state) {
    final float spring = 60f * chain.stiffness();
    final float damping = 30f * chain.damping();
    for (int i = 0; i < joints.size(); i++) {
      final int jointIndex = joints.get(i);
      final int poseBase = jointIndex * 4;
      final int stateQuatBase = i * 4;
      final int stateVelBase = i * 3;
      final float tx = rotations[poseBase];
      final float ty = rotations[poseBase + 1];
      final float tz = rotations[poseBase + 2];
      final float tw = rotations[poseBase + 3];

      if (!state.initialized) {
        state.rotations[stateQuatBase] = tx;
        state.rotations[stateQuatBase + 1] = ty;
        state.rotations[stateQuatBase + 2] = tz;
        state.rotations[stateQuatBase + 3] = tw;
        continue;
      }

      float sx = state.rotations[stateQuatBase];
      float sy = state.rotations[stateQuatBase + 1];
      float sz = state.rotations[stateQuatBase + 2];
      float sw = state.rotations[stateQuatBase + 3];
      final float[] error = quatLogMul(tx, ty, tz, tw, -sx, -sy, -sz, sw);
      float vx = state.angularVelocities[stateVelBase];
      float vy = state.angularVelocities[stateVelBase + 1];
      float vz = state.angularVelocities[stateVelBase + 2];

      vx += (error[0] * spring - vx * damping) * dt;
      vy += (error[1] * spring - vy * damping) * dt;
      vz += (error[2] * spring - vz * damping) * dt;

      final float[] delta = quatExp(vx * dt, vy * dt, vz * dt);
      final float[] next = quatMul(delta[0], delta[1], delta[2], delta[3], sx, sy, sz, sw);
      sx = next[0];
      sy = next[1];
      sz = next[2];
      sw = next[3];

      if (chain.angularLimit() <= EPSILON) {
        sx = tx;
        sy = ty;
        sz = tz;
        sw = tw;
        vx = 0f;
        vy = 0f;
        vz = 0f;
      } else {
        final float[] relative = quatMul(-tx, -ty, -tz, tw, sx, sy, sz, sw);
        float angle = 2f * (float) Math.acos(clamp(relative[3], -1f, 1f));
        if (angle > Math.PI) {
          angle = (float) (2f * Math.PI - angle);
        }
        if (angle > chain.angularLimit()) {
          final float t = chain.angularLimit() / Math.max(angle, EPSILON);
          final float[] clamped = quatSlerp(tx, ty, tz, tw, sx, sy, sz, sw, t);
          sx = clamped[0];
          sy = clamped[1];
          sz = clamped[2];
          sw = clamped[3];
          vx = 0f;
          vy = 0f;
          vz = 0f;
        }
      }

      state.rotations[stateQuatBase] = sx;
      state.rotations[stateQuatBase + 1] = sy;
      state.rotations[stateQuatBase + 2] = sz;
      state.rotations[stateQuatBase + 3] = sw;
      state.angularVelocities[stateVelBase] = vx;
      state.angularVelocities[stateVelBase + 1] = vy;
      state.angularVelocities[stateVelBase + 2] = vz;
      rotations[poseBase] = sx;
      rotations[poseBase + 1] = sy;
      rotations[poseBase + 2] = sz;
      rotations[poseBase + 3] = sw;
    }
    state.initialized = true;
  }

  private static float[] quatMul(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    final float x = aw * bx + ax * bw + ay * bz - az * by;
    final float y = aw * by - ax * bz + ay * bw + az * bx;
    final float z = aw * bz + ax * by - ay * bx + az * bw;
    final float w = aw * bw - ax * bx - ay * by - az * bz;
    return normalize(x, y, z, w);
  }

  private static float[] quatLogMul(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      final float bx,
      final float by,
      final float bz,
      final float bw) {
    final float[] q = quatMul(ax, ay, az, aw, bx, by, bz, bw);
    final float qw = clamp(q[3], -1f, 1f);
    final float angle = 2f * (float) Math.acos(qw);
    final float s = (float) Math.sqrt(Math.max(1f - qw * qw, 0f));
    if (s < EPSILON || angle < EPSILON) {
      return new float[] {0f, 0f, 0f};
    }
    final float scale = angle / s;
    return new float[] {q[0] * scale, q[1] * scale, q[2] * scale};
  }

  private static float[] quatExp(final float vx, final float vy, final float vz) {
    final float angle = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    if (angle < EPSILON) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float half = angle * 0.5f;
    final float sinHalf = (float) Math.sin(half);
    final float scale = sinHalf / angle;
    return normalize(vx * scale, vy * scale, vz * scale, (float) Math.cos(half));
  }

  private static float[] quatSlerp(
      final float ax,
      final float ay,
      final float az,
      final float aw,
      float bx,
      float by,
      float bz,
      float bw,
      final float t) {
    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0f) {
      dot = -dot;
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
    }
    if (dot > 0.9995f) {
      return normalize(
          ax + t * (bx - ax),
          ay + t * (by - ay),
          az + t * (bz - az),
          aw + t * (bw - aw));
    }
    final float theta0 = (float) Math.acos(clamp(dot, -1f, 1f));
    final float theta = theta0 * t;
    final float sinTheta = (float) Math.sin(theta);
    final float sinTheta0 = (float) Math.sin(theta0);
    final float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
    final float s1 = sinTheta / sinTheta0;
    return normalize(
        s0 * ax + s1 * bx,
        s0 * ay + s1 * by,
        s0 * az + s1 * bz,
        s0 * aw + s1 * bw);
  }

  private static float[] normalize(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq < EPSILON) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = (float) (1.0 / Math.sqrt(lenSq));
    return new float[] {x * inv, y * inv, z * inv, w * inv};
  }

  private static float clamp(final float value, final float min, final float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static final class ChainState {
    private float[] rotations;
    private float[] angularVelocities;
    private boolean initialized;

    private ChainState(final int jointCount) {
      this.rotations = new float[jointCount * 4];
      this.angularVelocities = new float[jointCount * 3];
      this.initialized = false;
    }

    private void ensureSize(final int jointCount) {
      if (this.rotations.length == jointCount * 4) {
        return;
      }
      this.rotations = new float[jointCount * 4];
      this.angularVelocities = new float[jointCount * 3];
      this.initialized = false;
    }
  }
}
