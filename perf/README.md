# Animis Performance Baseline

Run benchmarks:

```bash
mvn -f animis-perf/pom.xml -DskipTests compile exec:java \
  -Dexec.mainClass=org.animis.perf.BenchmarkRunner
```

Check against baseline targets:

```bash
./perf/check_baseline.sh
```

Files:
- `perf/baseline.csv`: threshold targets in `ms/op`
- `perf/latest.csv`: latest JMH output (`csv`)
