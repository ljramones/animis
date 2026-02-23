package org.animis.runtime.api;

import org.animis.motion.MotionDatabase;
import org.animis.motion.MotionFrame;

public interface MotionMatcher {
  MotionFrame findBest(MotionDatabase db, MotionQuery query);
}
