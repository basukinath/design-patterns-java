import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record Price(String sku, long cents) {}
interface CatalogClient {
    Price fetch(String sku) throws IOException;
}
static final class TransientFailure extends IOException {
    private static final long serialVersionUID = 1L;
    TransientFailure(String message) { super(message); }
}
record Observation(long elapsedNanos, boolean success) {}
record TimedCatalog(CatalogClient delegate, LongSupplier ticker,
                    Consumer<Observation> sink) implements CatalogClient {
    TimedCatalog {
        Objects.requireNonNull(delegate);
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(sink);
    }
    public Price fetch(String sku) throws IOException {
        long started = ticker.getAsLong();
        boolean success = false;
        try {
            Price result = delegate.fetch(sku);
            success = true;
            return result;
        } finally {
            var observation = new Observation(ticker.getAsLong() - started, success);
            try { sink.accept(observation); }
            catch (RuntimeException ignored) {
                // Telemetry is best effort; retain the operation's result.
            }
        }
    }
}
record RetryingCatalog(CatalogClient delegate, int maxAttempts)
        implements CatalogClient {
    RetryingCatalog {
        Objects.requireNonNull(delegate);
        if (maxAttempts < 1) throw new IllegalArgumentException("No attempts");
    }
    public Price fetch(String sku) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try { return delegate.fetch(sku); }
            catch (TransientFailure failure) {
                if (attempt == maxAttempts) throw failure;
            }
        }
    }
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
int[] calls = {0};
CatalogClient flaky = sku -> {
    if (++calls[0] == 1) throw new TransientFailure("temporary");
    return new Price(sku, 250);
};
var events = new ArrayList<Observation>();
long[] time = {0};
CatalogClient logical = new TimedCatalog(new RetryingCatalog(flaky, 2), () -> time[0] += 10, events::add);
check(logical.fetch("sku-1").cents() == 250);
check(calls[0] == 2 && events.size() == 1 && events.get(0).success());
calls[0] = 0;
events.clear();
new RetryingCatalog(new TimedCatalog(flaky, () -> time[0] += 10, events::add), 2).fetch("sku-1");
check(events.size() == 2 && !events.get(0).success() && events.get(1).success());
expect(IOException.class, () -> new RetryingCatalog(s -> { throw new IOException("permanent"); }, 3).fetch("x"));
check(new TimedCatalog(s -> new Price(s, 7), () -> 1, e -> { throw new IllegalStateException(); }).fetch("x").cents() == 7);
expect(TransientFailure.class, () -> new RetryingCatalog(s -> { throw new TransientFailure("down"); }, 2).fetch("x"));
expect(IllegalArgumentException.class, () -> new RetryingCatalog(flaky, 0));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

