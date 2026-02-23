package org.animis.perf;

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
public class SkinningComputerBenchmark {
  @State(Scope.Benchmark)
  public static class BenchState {
    BenchmarkFixtures.SkinningFixture fixture;

    @Setup
    public void setup() {
      this.fixture = BenchmarkFixtures.skinningFixture(100);
    }
  }

  @Benchmark
  public int compute100Joints(final BenchState state) {
    return state.fixture.computer().compute(state.fixture.skeleton(), state.fixture.pose()).jointMatrices().length;
  }
}
