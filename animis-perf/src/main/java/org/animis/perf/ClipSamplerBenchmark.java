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
public class ClipSamplerBenchmark {
  @State(Scope.Benchmark)
  public static class BenchState {
    BenchmarkFixtures.SamplingFixture fixture;
    float t;

    @Setup
    public void setup() {
      this.fixture = BenchmarkFixtures.samplingFixture(100, 1_801, 60f);
      this.t = 0f;
    }
  }

  @Benchmark
  public float sample60SecondClip(final BenchState state) {
    final float prev = state.t;
    state.t += 1f / 60f;
    state.fixture.sampler().sample(state.fixture.clip(), state.fixture.skeleton(), state.t, prev, true, state.fixture.outPose());
    return state.fixture.outPose().localTranslations()[0];
  }
}
