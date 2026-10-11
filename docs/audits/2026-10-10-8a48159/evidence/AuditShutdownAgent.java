import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;

/** Audit-only process control. No transformations; lets the packaged JVM run shutdown hooks. */
public final class AuditShutdownAgent {
    public static void premain(String ignored, Instrumentation instrumentation) {
        Thread.ofPlatform().daemon().name("audit-shutdown").start(() -> {
            try {
                var input = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    String line = input.readLine();
                    if (line == null) return;
                    if (line.equals("shutdown")) System.exit(0);
                }
            } catch (Exception error) { error.printStackTrace(); }
        });
    }
}
