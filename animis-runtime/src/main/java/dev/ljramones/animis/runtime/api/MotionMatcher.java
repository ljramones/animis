package dev.ljramones.animis.runtime.api;

import dev.ljramones.animis.motion.MotionDatabase;
import dev.ljramones.animis.motion.MotionFrame;

public interface MotionMatcher {
  MotionFrame findBest(MotionDatabase db, MotionQuery query);
}
