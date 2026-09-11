import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record AppConfig(java.net.URI reportEndpoint, Duration timeout) {
    AppConfig {
        Objects.requireNonNull(reportEndpoint);
        Objects.requireNonNull(timeout);
        if (!reportEndpoint.isAbsolute() || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Invalid configuration");
    }
}
static final class ExportService {
    private final AppConfig config;
    ExportService(AppConfig config) { this.config = Objects.requireNonNull(config); }
    java.net.URI endpoint() { return config.reportEndpoint(); }
}
enum ProcessSequence {
    INSTANCE;
    private final java.util.concurrent.atomic.AtomicLong next =
            new java.util.concurrent.atomic.AtomicLong();
    long nextValue() { return next.incrementAndGet(); }
    void resetForExample() { next.set(0); }
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var firstConfig = new AppConfig(java.net.URI.create("https://a.example"), Duration.ofSeconds(1));
var secondConfig = new AppConfig(java.net.URI.create("https://b.example"), Duration.ofSeconds(2));
check(!new ExportService(firstConfig).endpoint().equals(new ExportService(secondConfig).endpoint()));
expect(IllegalArgumentException.class, () -> new AppConfig(java.net.URI.create("/relative"), Duration.ofSeconds(1)));
expect(IllegalArgumentException.class, () -> new AppConfig(java.net.URI.create("https://a.example"), Duration.ZERO));
ProcessSequence.INSTANCE.resetForExample();
check(ProcessSequence.INSTANCE.nextValue() == 1);
check(ProcessSequence.INSTANCE.nextValue() == 2);
check(ProcessSequence.INSTANCE == ProcessSequence.valueOf("INSTANCE"));
check(ProcessSequence.values().length == 1);
System.out.println("PASS: "+checks+" behavioral checks");
}
}

