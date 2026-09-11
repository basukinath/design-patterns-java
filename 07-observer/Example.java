import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record OrderPlaced(UUID eventId, String orderId, Instant occurredAt) {
    OrderPlaced {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(occurredAt);
        if (orderId.isBlank()) throw new IllegalArgumentException("Blank order id");
    }
}
interface OrderListener {
    void on(OrderPlaced event);
}
static final class OrderEvents {
    private final List<OrderListener> listeners;
    OrderEvents(List<OrderListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }
    void publish(OrderPlaced event) {
        Objects.requireNonNull(event);
        for (var listener : listeners) listener.on(event);
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
var event = new OrderPlaced(UUID.randomUUID(), "order-17", Instant.parse("2026-01-01T00:00:00Z"));
var calls = new ArrayList<String>();
var registrations = new ArrayList<OrderListener>();
registrations.add(e -> calls.add("dashboard:" + e.orderId()));
registrations.add(e -> calls.add("counter:" + e.eventId()));
var events = new OrderEvents(registrations);
registrations.clear();
events.publish(event);
check(calls.size() == 2);
check(calls.get(0).equals("dashboard:order-17"));
check(calls.get(1).equals("counter:" + event.eventId()));
int[] later = {0};
var failure = new IllegalStateException("listener failed");
var failing = new OrderEvents(List.of(e -> { throw failure; }, e -> later[0]++));
expect(IllegalStateException.class, () -> failing.publish(event));
check(later[0] == 0);
new OrderEvents(List.of()).publish(event);
expect(NullPointerException.class, () -> events.publish(null));
expect(IllegalArgumentException.class, () -> new OrderPlaced(UUID.randomUUID(), "", Instant.now()));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

