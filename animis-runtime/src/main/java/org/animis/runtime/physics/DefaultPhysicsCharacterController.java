package org.animis.runtime.physics;

import org.animis.runtime.pose.PoseBuffer;
import org.animis.skeleton.Joint;
import org.animis.skeleton.Skeleton;
import org.dynamisengine.collision.constraints.PointConstraint3D;
import org.dynamisengine.collision.contact.ContactSolver3D;
import org.dynamisengine.collision.world.PhysicsStep3D;
import org.dynamisengine.collision.world.RigidBodyAdapter3D;
import org.dynamisengine.vectrix.core.Vector3d;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DefaultPhysicsCharacterController implements PhysicsCharacterController {
  private static final float EPSILON = 1.0e-6f;

  private final PhysicsCharacterDef def;
  private final JointBodyAdapter adapter = new JointBodyAdapter();
  private final ContactSolver3D<JointBody> contactSolver = new ContactSolver3D<>(adapter);
  private final PhysicsStep3D physicsStep = new PhysicsStep3D(1.0 / 120.0, 8);
  private final Map<Integer, JointBody> bodies = new HashMap<>();
  private final Set<Integer> driven;
  private final Set<Integer> keyframed;

  private PoseBuffer simulated;

  public DefaultPhysicsCharacterController(final PhysicsCharacterDef def) {
    this.def = def;
    this.driven = new HashSet<>(def.drivenJoints());
    this.keyframed = new HashSet<>(def.keyframedJoints());
  }

  @Override
  public void update(final PoseBuffer targetPose, final Skeleton skeleton, final float dt) {
    if (targetPose == null || skeleton == null) {
      throw new IllegalArgumentException("targetPose and skeleton must be non-null");
    }
    final int jointCount = skeleton.joints().size();
    if (targetPose.jointCount() < jointCount) {
      throw new IllegalArgumentException("PoseBuffer jointCount is smaller than skeleton joint count");
    }
    if (simulated == null || simulated.jointCount() != targetPose.jointCount()) {
      simulated = new PoseBuffer(targetPose.jointCount());
      copyPose(targetPose, simulated);
    } else {
      copyPose(targetPose, simulated);
    }
    if (dt <= 0f || driven.isEmpty()) {
      return;
    }

    final float[] targetWorldP = new float[jointCount * 3];
    final float[] targetWorldQ = new float[jointCount * 4];
    computeWorldTransforms(skeleton, targetPose, targetWorldP, targetWorldQ);

    for (final int joint : driven) {
      if (joint < 0 || joint >= jointCount || keyframed.contains(joint)) {
        continue;
      }
      final int p = joint * 3;
      final JointBody body = bodies.computeIfAbsent(joint, ignored -> {
        final JointBody b = new JointBody();
        b.position = new Vector3d(targetWorldP[p], targetWorldP[p + 1], targetWorldP[p + 2]);
        b.velocity = new Vector3d();
        return b;
      });

      final Vector3d target = new Vector3d(targetWorldP[p], targetWorldP[p + 1], targetWorldP[p + 2]);
      final Vector3d error = new Vector3d(target).sub(body.position);
      final Vector3d accel = error.mul(40.0 * def.stiffness()).sub(new Vector3d(body.velocity).mul(12.0 * def.damping()));
      clampVector(accel, def.maxTorque() * Math.max(0.05f, def.stiffness()));
      body.velocity.add(new Vector3d(accel).mul(dt));

      // Constraint pass keeps the driven body anchored toward the target point.
      if (def.stiffness() >= 0.999f) {
        final PointConstraint3D<JointBody> point = new PointConstraint3D<>(body, target, 1e-6);
        point.solve(adapter, dt);
      }
    }

    physicsStep.advance(dt, subDt -> integrate(subDt));

    final float[] simWorldP = targetWorldP.clone();
    for (final int joint : driven) {
      if (joint < 0 || joint >= jointCount || keyframed.contains(joint)) {
        continue;
      }
      final JointBody body = bodies.get(joint);
      if (body == null) {
        continue;
      }
      final int b = joint * 3;
      simWorldP[b] = (float) body.position.x;
      simWorldP[b + 1] = (float) body.position.y;
      simWorldP[b + 2] = (float) body.position.z;
    }

    applyWorldTranslationsToPose(simulated, skeleton, simWorldP, targetWorldQ, driven, keyframed);
    // Access to the responder keeps the integration path aligned with DynamisCollision contact API shape.
    contactSolver.setPositionCorrectionSlop(0.01);
  }

  @Override
  public PoseBuffer simulatedPose() {
    return simulated;
  }

  private void integrate(final double subDt) {
    for (final int joint : driven) {
      if (keyframed.contains(joint)) {
        continue;
      }
      final JointBody body = bodies.get(joint);
      if (body == null) {
        continue;
      }
      body.position.add(new Vector3d(body.velocity).mul(subDt));
      body.velocity.mul(Math.max(0.0, 1.0 - def.damping() * subDt));
    }
  }

  private static void copyPose(final PoseBuffer from, final PoseBuffer to) {
    System.arraycopy(from.localTranslations(), 0, to.localTranslations(), 0, from.localTranslations().length);
    System.arraycopy(from.localRotations(), 0, to.localRotations(), 0, from.localRotations().length);
    System.arraycopy(from.localScales(), 0, to.localScales(), 0, from.localScales().length);
  }

  private static void clampVector(final Vector3d vector, final float max) {
    if (max <= EPSILON) {
      vector.zero();
      return;
    }
    final double len = vector.length();
    if (len <= max || len <= EPSILON) {
      return;
    }
    vector.mul(max / len);
  }

  private static void applyWorldTranslationsToPose(
      final PoseBuffer pose,
      final Skeleton skeleton,
      final float[] worldPositions,
      final float[] worldRotations,
      final Set<Integer> driven,
      final Set<Integer> keyframed) {
    for (final Joint joint : skeleton.joints()) {
      final int i = joint.index();
      if (!driven.contains(i) || keyframed.contains(i)) {
        continue;
      }
      final int wb = i * 3;
      if (joint.parentIndex() < 0) {
        pose.setTranslation(i, worldPositions[wb], worldPositions[wb + 1], worldPositions[wb + 2]);
        continue;
      }
      final int p = joint.parentIndex();
      final int pb = p * 3;
      final float dx = worldPositions[wb] - worldPositions[pb];
      final float dy = worldPositions[wb + 1] - worldPositions[pb + 1];
      final float dz = worldPositions[wb + 2] - worldPositions[pb + 2];
      final int pr = p * 4;
      final float[] local = rotateByInverse(
          worldRotations[pr], worldRotations[pr + 1], worldRotations[pr + 2], worldRotations[pr + 3], dx, dy, dz);
      pose.setTranslation(i, local[0], local[1], local[2]);
    }
  }

  private static void computeWorldTransforms(
      final Skeleton skeleton,
      final PoseBuffer pose,
      final float[] worldP,
      final float[] worldQ) {
    final float[] t = pose.localTranslations();
    final float[] r = pose.localRotations();
    for (final Joint joint : skeleton.joints()) {
      final int i = joint.index();
      final int tb = i * 3;
      final int rb = i * 4;
      final float lx = t[tb];
      final float ly = t[tb + 1];
      final float lz = t[tb + 2];
      final float lqx = r[rb];
      final float lqy = r[rb + 1];
      final float lqz = r[rb + 2];
      final float lqw = r[rb + 3];
      if (joint.parentIndex() < 0) {
        worldP[tb] = lx;
        worldP[tb + 1] = ly;
        worldP[tb + 2] = lz;
        final float[] n = normalizeQuat(lqx, lqy, lqz, lqw);
        worldQ[rb] = n[0];
        worldQ[rb + 1] = n[1];
        worldQ[rb + 2] = n[2];
        worldQ[rb + 3] = n[3];
      } else {
        final int p = joint.parentIndex();
        final int pt = p * 3;
        final int pr = p * 4;
        final float[] rotated = rotateBy(worldQ[pr], worldQ[pr + 1], worldQ[pr + 2], worldQ[pr + 3], lx, ly, lz);
        worldP[tb] = worldP[pt] + rotated[0];
        worldP[tb + 1] = worldP[pt + 1] + rotated[1];
        worldP[tb + 2] = worldP[pt + 2] + rotated[2];
        final float[] q = quatMul(worldQ[pr], worldQ[pr + 1], worldQ[pr + 2], worldQ[pr + 3], lqx, lqy, lqz, lqw);
        worldQ[rb] = q[0];
        worldQ[rb + 1] = q[1];
        worldQ[rb + 2] = q[2];
        worldQ[rb + 3] = q[3];
      }
    }
  }

  private static float[] rotateBy(final float qx, final float qy, final float qz, final float qw, final float x, final float y, final float z) {
    final float[] t = quatMulRaw(qx, qy, qz, qw, x, y, z, 0f);
    final float[] r = quatMulRaw(t[0], t[1], t[2], t[3], -qx, -qy, -qz, qw);
    return new float[] {r[0], r[1], r[2]};
  }

  private static float[] rotateByInverse(final float qx, final float qy, final float qz, final float qw, final float x, final float y, final float z) {
    return rotateBy(-qx, -qy, -qz, qw, x, y, z);
  }

  private static float[] quatMul(
      final float ax, final float ay, final float az, final float aw,
      final float bx, final float by, final float bz, final float bw) {
    final float[] raw = quatMulRaw(ax, ay, az, aw, bx, by, bz, bw);
    return normalizeQuat(raw[0], raw[1], raw[2], raw[3]);
  }

  private static float[] quatMulRaw(
      final float ax, final float ay, final float az, final float aw,
      final float bx, final float by, final float bz, final float bw) {
    return new float[] {
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz
    };
  }

  private static float[] normalizeQuat(final float x, final float y, final float z, final float w) {
    final float lenSq = x * x + y * y + z * z + w * w;
    if (lenSq < EPSILON) {
      return new float[] {0f, 0f, 0f, 1f};
    }
    final float inv = (float) (1.0 / Math.sqrt(lenSq));
    return new float[] {x * inv, y * inv, z * inv, w * inv};
  }

  private static final class JointBody {
    private Vector3d position;
    private Vector3d velocity;
  }

  private static final class JointBodyAdapter implements RigidBodyAdapter3D<JointBody> {
    @Override
    public Vector3d getPosition(final JointBody body) {
      return body.position;
    }

    @Override
    public void setPosition(final JointBody body, final Vector3d position) {
      body.position = new Vector3d(position);
    }

    @Override
    public Vector3d getVelocity(final JointBody body) {
      return body.velocity;
    }

    @Override
    public void setVelocity(final JointBody body, final Vector3d velocity) {
      body.velocity = new Vector3d(velocity);
    }

    @Override
    public double getInverseMass(final JointBody body) {
      return 1.0;
    }

    @Override
    public double getRestitution(final JointBody body) {
      return 0.0;
    }

    @Override
    public double getFriction(final JointBody body) {
      return 0.9;
    }
  }
}
