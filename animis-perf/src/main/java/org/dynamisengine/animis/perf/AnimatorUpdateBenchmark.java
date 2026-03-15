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
public class AnimatorUpdateBenchmark {
  @State(Scope.Benchmark)
  public static class BenchState {
    BenchmarkFixtures.AnimatorRuntimeFixture fixture;

    @Setup
    public void setup() {
      this.fixture = BenchmarkFixtures.animatorFixture(100);
    }
  }

  @Benchmark
  public float update100Joints(final BenchState state) {
    state.fixture.animator().update(1f / 60f);
    return state.fixture.animator().pose().localTranslations()[0];
  }
}
