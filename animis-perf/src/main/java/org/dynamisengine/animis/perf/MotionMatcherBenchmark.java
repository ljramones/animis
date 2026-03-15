package org.dynamisengine.animis.perf;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class MotionMatcherBenchmark {
  @State(Scope.Benchmark)
  public static class BenchState {
    BenchmarkFixtures.MotionFixture fixture;

    @Setup
    public void setup() {
      this.fixture = BenchmarkFixtures.motionFixture(10_000, 64, 12, 2);
    }
  }

  @Benchmark
  public int findBest10kFrames(final BenchState state) {
    return state.fixture.matcher().findBest(state.fixture.db(), state.fixture.query()).clipIndex();
  }
}
