import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
enum PaymentStatus { UNPAID, PAID, REFUNDED }
record Order(PaymentStatus status, long totalCents) {
    Order {
        Objects.requireNonNull(status);
        if (totalCents < 0) throw new IllegalArgumentException("Negative total");
    }
}
@FunctionalInterface
interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
    default Specification<T> and(Specification<T> other) {
        Objects.requireNonNull(other);
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }
    default Specification<T> or(Specification<T> other) {
        Objects.requireNonNull(other);
        return candidate -> isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }
    default Specification<T> not() {
        return candidate -> !isSatisfiedBy(candidate);
    }
}
record MinimumTotal(long cents) implements Specification<Order> {
    MinimumTotal {
        if (cents < 0) throw new IllegalArgumentException("Negative threshold");
    }
    public boolean isSatisfiedBy(Order order) {
        return order.totalCents() >= cents;
    }
}
static Specification<Order> paid() {
    return order -> order.status() == PaymentStatus.PAID;
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var rule = paid().and(new MinimumTotal(10_000));
check(rule.isSatisfiedBy(new Order(PaymentStatus.PAID, 10_000)));
check(!rule.isSatisfiedBy(new Order(PaymentStatus.PAID, 9_999)));
check(!rule.isSatisfiedBy(new Order(PaymentStatus.UNPAID, 20_000)));
check(paid().not().isSatisfiedBy(new Order(PaymentStatus.REFUNDED, 1)));
check(paid().or(new MinimumTotal(10_000)).isSatisfiedBy(new Order(PaymentStatus.UNPAID, 10_000)));
Specification<Order> unexpected = order -> { throw new AssertionError("Should short-circuit"); };
check(!paid().and(unexpected).isSatisfiedBy(new Order(PaymentStatus.UNPAID, 0)));
expect(IllegalArgumentException.class, () -> new MinimumTotal(-1));
expect(NullPointerException.class, () -> paid().and(null));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

