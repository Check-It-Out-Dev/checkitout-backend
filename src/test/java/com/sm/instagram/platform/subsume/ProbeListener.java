package com.sm.instagram.platform.subsume;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Per-test coverage probes for the subsumption analysis — the backend half of the instrument
 * whose contracts live in the frontend repository at {@code tools/subsume/README.md}, decided in
 * {@code docs/ci/ADR-test-subsumption.md} there.
 *
 * <p>Registered through {@code META-INF/services} and inert unless the JVM carries
 * {@code -Dsubsume.probes=true} — surefire forwards that user property from the Maven command line
 * into the fork, so {@code ./mvnw test -Ptest -Dsubsume.probes=true} is the whole switch. After
 * every test it takes the running JaCoCo agent's execution data <em>and resets it</em>, so each
 * snapshot is exactly what that one test exercised. JaCoCo probes are booleans — once an earlier
 * test has flipped one it stays flipped, so a diff of cumulative snapshots would credit a line to
 * the first test that reached it and to no other; only a reset per test yields per-test coverage
 * (istanbul's counters are counts, which is why the Jest hook can diff instead).
 *
 * <p>Resetting would leave the exec file the agent writes at JVM exit holding only the last test,
 * and the coverage report the tier feeds to Sonar with it. So the listener keeps the union of
 * everything it collected and appends that union to the same exec file before the JVM exits; the
 * report merges the blocks and comes out whole. {@code exec-check.json} records that the file read
 * back contains every probe of the union. One JSON line per test goes to
 * {@code target/subsume/probes.jsonl}; probes that flipped before the first test or after the last
 * are attributed to {@code (plan setup)} and {@code (plan teardown)}.
 *
 * <p>Only classes under {@code com.sm.instagram} are considered (the agent instruments nothing
 * else, see the {@code prepare-agent} execution in {@code pom.xml}), and only those with a
 * class file under {@code target/classes} are mapped: {@code classes.json} says, for every probe
 * of every hit class, which source lines and branches it stands for — computed by analysing the
 * class file once per probe with that single probe set, because JaCoCo exposes line coverage for a
 * data store, not a probe-to-line table.
 */
public final class ProbeListener implements TestExecutionListener {

  private static final String PREFIX = "com/sm/instagram/";
  private static final Pattern CLASS_SEGMENT = Pattern.compile("\\[class:([^\\]]+)\\]");
  private static final Path CLASSES = Paths.get("target", "classes");
  private static final Path DIR = Paths.get("target", "subsume");

  private static final Path EXEC = Paths.get(System.getProperty("subsume.exec", "target/jacoco-unit.exec"));

  private boolean armed = Boolean.getBoolean("subsume.probes");
  private IAgent agent;
  private Writer out;
  private final long planStartedAt = System.currentTimeMillis();
  private final Map<String, boolean[]> union = new TreeMap<>();
  private final Map<String, Long> ids = new TreeMap<>();
  private final Map<String, Long> startedAt = new java.util.HashMap<>();
  private int tests;

  @Override
  public void executionStarted(TestIdentifier id) {
    if (armed && id.isTest()) {
      startedAt.put(id.getUniqueId(), System.nanoTime());
    }
  }

  @Override
  public void testPlanExecutionStarted(TestPlan plan) {
    if (!armed) {
      return;
    }
    try {
      agent = RT.getAgent();
    } catch (IllegalStateException e) {
      System.err.println("subsume: no JaCoCo agent in this JVM; probes off");
      armed = false;
      return;
    }
    try {
      Files.createDirectories(DIR);
      out = Files.newBufferedWriter(DIR.resolve("probes.jsonl"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    record("(plan setup)", "(plan setup)", snapshot(), -1);
  }

  @Override
  public void executionFinished(TestIdentifier id, TestExecutionResult result) {
    if (!armed || !id.isTest()) {
      return;
    }
    Map<String, boolean[]> hits = snapshot();
    Long started = startedAt.remove(id.getUniqueId());
    double seconds = started == null ? -1 : (System.nanoTime() - started) / 1e9;
    record(id.getUniqueId(), specOf(id), hits, seconds);
    tests++;
  }

  @Override
  public void testPlanExecutionFinished(TestPlan plan) {
    if (!armed) {
      return;
    }
    record("(plan teardown)", "(plan teardown)", snapshot(), -1);
    StringBuilder totals = new StringBuilder();
    int classes = 0;
    for (Map.Entry<String, boolean[]> e : union.entrySet()) {
      int n = count(e.getValue());
      if (n == 0) {
        continue;
      }
      classes++;
      totals.append(totals.length() > 0 ? "," : "").append('"').append(dotted(e.getKey())).append("\":").append(n);
    }
    write("{\"final\":true,\"totals\":{" + totals + "}}\n");
    try {
      out.close();
      writeClasses(union);
      appendUnionToExec();
      List<String> missing = readBackExec();
      String check = "{\"tests\":" + tests + ",\"classes\":" + classes + ",\"exec\":\"" + json(EXEC.toString())
          + "\",\"missingFromExec\":[" + joinJson(missing) + "]}\n";
      Files.writeString(DIR.resolve("exec-check.json"), check, StandardCharsets.UTF_8);
      System.out.println("subsume: " + tests + " tests, " + classes + " classes with hits, "
          + (missing.isEmpty() ? "exec-check OK" : missing.size() + " classes MISSING from the exec file — see target/subsume/exec-check.json"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The union of every snapshot, appended to the agent's exec file so the coverage report stays whole. */
  private void appendUnionToExec() throws IOException {
    Files.createDirectories(EXEC.toAbsolutePath().getParent());
    try (java.io.OutputStream os = Files.newOutputStream(EXEC, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
      org.jacoco.core.data.ExecutionDataWriter w = new org.jacoco.core.data.ExecutionDataWriter(os);
      w.visitSessionInfo(new org.jacoco.core.data.SessionInfo("subsume-union", planStartedAt, System.currentTimeMillis()));
      for (Map.Entry<String, boolean[]> e : union.entrySet()) {
        if (count(e.getValue()) > 0) {
          w.visitClassExecution(new ExecutionData(ids.get(e.getKey()), e.getKey(), e.getValue()));
        }
      }
      w.flush();
    }
  }

  /** Classes of the union whose probes the exec file, read back and merged, does not contain. */
  private List<String> readBackExec() throws IOException {
    ExecutionDataStore store = new ExecutionDataStore();
    try (java.io.InputStream in = Files.newInputStream(EXEC)) {
      ExecutionDataReader r = new ExecutionDataReader(in);
      r.setSessionInfoVisitor(info -> { });
      r.setExecutionDataVisitor(store);
      r.read();
    }
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, boolean[]> e : union.entrySet()) {
      ExecutionData d = store.get(ids.get(e.getKey()));
      boolean[] u = e.getValue();
      boolean[] f = d == null ? new boolean[0] : d.getProbes();
      for (int i = 0; i < u.length; i++) {
        if (u[i] && (i >= f.length || !f[i])) {
          missing.add(dotted(e.getKey()));
          break;
        }
      }
    }
    return missing;
  }

  // ── snapshots ────────────────────────────────────────────────────────────────────────────────

  /** What ran since the previous snapshot — the agent is reset — for production classes only,
   *  folded into the running union. Classes with no probe hit are dropped. */
  private Map<String, boolean[]> snapshot() {
    Map<String, boolean[]> into = new TreeMap<>();
    ExecutionDataReader reader =
        new ExecutionDataReader(new ByteArrayInputStream(agent.getExecutionData(true)));
    reader.setSessionInfoVisitor(info -> { });
    reader.setExecutionDataVisitor(data -> {
      if (!data.getName().startsWith(PREFIX) || !isMainClass(data.getName()) || count(data.getProbes()) == 0) {
        return;
      }
      into.merge(data.getName(), data.getProbes().clone(), ProbeListener::or);
      ids.putIfAbsent(data.getName(), data.getId());
    });
    try {
      reader.read();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (Map.Entry<String, boolean[]> e : into.entrySet()) {
      union.merge(e.getKey(), e.getValue().clone(), ProbeListener::or);
    }
    return into;
  }

  private final Map<String, Boolean> mainClass = new TreeMap<>();

  /** Test classes share the package prefix but live under target/test-classes; only production
   *  classes — those with a class file under target/classes — are part of the matrix. */
  private boolean isMainClass(String vmName) {
    return mainClass.computeIfAbsent(vmName, n -> Files.exists(CLASSES.resolve(n + ".class")));
  }

  private static boolean[] or(boolean[] a, boolean[] b) {
    boolean[] r = new boolean[Math.max(a.length, b.length)];
    for (int i = 0; i < r.length; i++) {
      r[i] = (i < a.length && a[i]) || (i < b.length && b[i]);
    }
    return r;
  }

  private static int count(boolean[] bits) {
    if (bits == null) {
      return 0;
    }
    int n = 0;
    for (boolean b : bits) {
      if (b) {
        n++;
      }
    }
    return n;
  }

  // ── records ──────────────────────────────────────────────────────────────────────────────────

  private void record(String test, String spec, Map<String, boolean[]> hits, double seconds) {
    StringBuilder sb = new StringBuilder("{\"test\":\"").append(json(test))
        .append("\",\"spec\":\"").append(json(spec))
        .append("\",\"seconds\":").append(seconds < 0 ? "null" : String.format(java.util.Locale.ROOT, "%.4f", seconds))
        .append(",\"hits\":{");
    boolean first = true;
    for (Map.Entry<String, boolean[]> e : hits.entrySet()) {
      sb.append(first ? "" : ",").append('"').append(dotted(e.getKey())).append("\":\"").append(pack(e.getValue())).append('"');
      first = false;
    }
    write(sb.append("}}\n").toString());
  }

  /**
   * The test's identity is JUnit's unique id, verbatim — {@code [engine:junit-jupiter]/[class:…]/
   * [nested-class:…]/[method:…]/[test-template-invocation:#n]} — because that is the string PIT
   * writes into {@code <killingTests>}, so the two artefacts join on it exactly. The spec is the
   * binary class name from the source, nested classes included, so a demotion tag can be placed.
   */
  static String specOf(TestIdentifier id) {
    return id.getSource()
        .filter(MethodSource.class::isInstance)
        .map(MethodSource.class::cast)
        .map(MethodSource::getClassName)
        .orElse(CLASS_SEGMENT.matcher(id.getUniqueId()).results()
            .map(r -> r.group(1)).findFirst().orElse(id.getUniqueId()));
  }

  /** Bit-packed, least significant bit first, base64. */
  static String pack(boolean[] bits) {
    byte[] bytes = new byte[(bits.length + 7) / 8];
    for (int i = 0; i < bits.length; i++) {
      if (bits[i]) {
        bytes[i / 8] |= (byte) (1 << (i % 8));
      }
    }
    return Base64.getEncoder().encodeToString(bytes);
  }

  // ── classes.json ─────────────────────────────────────────────────────────────────────────────

  /**
   * For every hit class with a class file under target/classes: source file, JaCoCo class id, and
   * per probe the lines and branch lines that probe alone covers.
   */
  private void writeClasses(Map<String, boolean[]> fin) throws IOException {
    // one analysis per probe per class — ~40k small ASM passes; independent, so in parallel
    List<String> entries = fin.entrySet().parallelStream()
        .filter(e -> count(e.getValue()) > 0 && Files.exists(CLASSES.resolve(e.getKey() + ".class")))
        .map(e -> classEntry(e.getKey(), e.getValue().length))
        .toList();
    Files.writeString(DIR.resolve("classes.json"), "{" + String.join(",", entries) + "}\n", StandardCharsets.UTF_8);
  }

  private String classEntry(String name, int probes) {
    try {
      byte[] bytes = Files.readAllBytes(CLASSES.resolve(name + ".class"));
      String sourceFile = null;
      StringBuilder perProbe = new StringBuilder();
      for (int i = 0; i < probes; i++) {
        boolean[] one = new boolean[probes];
        one[i] = true;
        ExecutionDataStore store = new ExecutionDataStore();
        store.put(new ExecutionData(ids.get(name), name, one));
        CoverageBuilder builder = new CoverageBuilder();
        try {
          new Analyzer(store, builder).analyzeClass(bytes, name);
        } catch (IOException | IllegalStateException ex) {
          // a class file whose bytes no longer match the loaded class: leave its probes unmapped
          perProbe.append(i > 0 ? "," : "").append("{\"method\":\"\",\"lines\":[],\"branchLines\":[],\"error\":\"")
              .append(json(ex.getClass().getSimpleName() + ": " + ex.getMessage())).append("\"}");
          continue;
        }
        if (builder.getClasses().isEmpty()) {
          perProbe.append(i > 0 ? "," : "").append("{\"method\":\"\",\"lines\":[],\"branchLines\":[]}");
          continue;
        }
        IClassCoverage cc = builder.getClasses().iterator().next();
        sourceFile = cc.getSourceFileName();
        String method = "";
        List<Integer> lines = new ArrayList<>();
        List<Integer> branchLines = new ArrayList<>();
        for (IMethodCoverage mc : cc.getMethods()) {
          for (int line = mc.getFirstLine(); line <= mc.getLastLine(); line++) {
            if (line < 0) {
              continue;
            }
            ILine l = mc.getLine(line);
            boolean covered = l.getInstructionCounter().getCoveredCount() > 0;
            boolean branch = l.getBranchCounter().getCoveredCount() > 0;
            if (covered || branch) {
              method = mc.getName() + mc.getDesc();
              if (covered) {
                lines.add(line);
              }
              if (branch) {
                branchLines.add(line);
              }
            }
          }
        }
        perProbe.append(i > 0 ? "," : "").append("{\"method\":\"").append(json(method))
            .append("\",\"lines\":").append(lines).append(",\"branchLines\":").append(branchLines).append('}');
      }
      String pkg = name.contains("/") ? name.substring(0, name.lastIndexOf('/')) : "";
      String file = sourceFile == null ? "" : "src/main/java/" + pkg + "/" + sourceFile;
      return "\"" + dotted(name) + "\":{\"file\":\"" + json(file) + "\",\"id\":\""
          + Long.toHexString(ids.get(name)) + "\",\"probes\":[" + perProbe + "]}";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ── small helpers ────────────────────────────────────────────────────────────────────────────

  private void write(String line) {
    try {
      out.write(line);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String dotted(String vmName) {
    return vmName.replace('/', '.');
  }

  private static String joinJson(List<String> items) {
    StringBuilder sb = new StringBuilder();
    for (String s : items) {
      sb.append(sb.length() > 0 ? "," : "").append('"').append(json(s)).append('"');
    }
    return sb.toString();
  }

  static String json(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 8);
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
