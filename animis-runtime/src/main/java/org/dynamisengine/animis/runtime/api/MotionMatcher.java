package org.dynamisengine.animis.runtime.api;

import org.dynamisengine.animis.motion.MotionDatabase;
import org.dynamisengine.animis.motion.MotionFrame;

public interface MotionMatcher {
  MotionFrame findBest(MotionDatabase db, MotionQuery query);
}
