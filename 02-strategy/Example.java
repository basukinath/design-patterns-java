import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record PricingInput(long subtotalCents) {
    PricingInput {
        if (subtotalCents < 0) throw new IllegalArgumentException("Negative subtotal");
    }
}
interface DiscountPolicy {
    long discount(PricingInput input);
}
record PercentageDiscount(int basisPoints) implements DiscountPolicy {
    PercentageDiscount {
        if (basisPoints < 0 || basisPoints > 10_000)
            throw new IllegalArgumentException("Rate outside 0..10000");
    }
    public long discount(PricingInput input) {
        return BigDecimal.valueOf(input.subtotalCents())
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
record ThresholdDiscount(long thresholdCents, long reductionCents)
        implements DiscountPolicy {
    ThresholdDiscount {
        if (thresholdCents < 0 || reductionCents < 0)
            throw new IllegalArgumentException("Negative policy value");
    }
    public long discount(PricingInput input) {
        return input.subtotalCents() >= thresholdCents
                ? Math.min(reductionCents, input.subtotalCents()) : 0;
    }
}
record Quote(long subtotalCents, long discountCents, long payableCents) {}
static final class PricingService {
    private final DiscountPolicy policy;
    PricingService(DiscountPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }
    Quote quote(PricingInput input) {
        Objects.requireNonNull(input);
        long discount = policy.discount(input);
        if (discount < 0 || discount > input.subtotalCents())
            throw new IllegalStateException("Policy returned an invalid discount");
        return new Quote(input.subtotalCents(), discount,
                input.subtotalCents() - discount);
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
var member = new PricingService(new PercentageDiscount(1000));
check(member.quote(new PricingInput(999)).discountCents() == 99);
check(member.quote(new PricingInput(0)).payableCents() == 0);
var contract = new PricingService(new ThresholdDiscount(10000, 1500));
check(contract.quote(new PricingInput(9999)).discountCents() == 0);
check(contract.quote(new PricingInput(10000)).payableCents() == 8500);
check(new PricingService(new ThresholdDiscount(0, 500)).quote(new PricingInput(200)).payableCents() == 0);
check(new PercentageDiscount(10000).discount(new PricingInput(Long.MAX_VALUE)) == Long.MAX_VALUE);
expect(IllegalArgumentException.class, () -> new PercentageDiscount(10001));
expect(IllegalArgumentException.class, () -> new PricingInput(-1));
expect(IllegalStateException.class, () -> new PricingService(i -> -1).quote(new PricingInput(10)));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

