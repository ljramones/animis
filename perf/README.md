# Animis Performance Baseline

Run benchmarks:

```bash
mvn -f animis-perf/pom.xml -DskipTests compile exec:java \
  -Dexec.mainClass=org.animis.perf.BenchmarkRunner
```

Run a comprehensive forked pass (canonical):

```bash
mvn -q -f animis-perf/pom.xml -DskipTests package
mvn -q -f animis-perf/pom.xml -DskipTests dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
CP="animis-perf/target/classes:$(cat animis-perf/target/classpath.txt)"
java -cp "$CP" org.openjdk.jmh.Main "org.animis.perf.*" \
  -wi 5 -i 10 -w 1s -r 1s -f 2 -tu ms -rf csv \
  -rff perf/comprehensive-$(date +%Y%m%d-%H%M%S).csv
cp perf/comprehensive-YYYYMMDD-HHMMSS.csv perf/latest.csv
```

Check against baseline targets:

```bash
./perf/check_baseline.sh
```

Files:
- `perf/baseline.csv`: threshold targets in `ms/op`
- `perf/latest.csv`: latest JMH output (`csv`)
- `perf/comprehensive-20260223-095130.csv`: canonical forked baseline run
