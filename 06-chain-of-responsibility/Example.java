import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
enum Decision { CLEAR, REVIEW, REJECT }
record RiskInput(boolean accountBlocked, long totalCents) {
    RiskInput {
        if (totalCents < 0) throw new IllegalArgumentException("Negative total");
    }
}
record Finding(Decision decision, String ruleId) {
    Finding {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(ruleId);
        if (decision == Decision.CLEAR || ruleId.isBlank())
            throw new IllegalArgumentException("Finding must stop the chain");
    }
}
interface RiskCheck {
    Optional<Finding> evaluate(RiskInput input);
}
static final class RiskChain {
    private final List<RiskCheck> checks;
    RiskChain(List<RiskCheck> checks) {
        this.checks = List.copyOf(checks);
        if (this.checks.isEmpty()) throw new IllegalArgumentException("No risk checks");
    }
    Optional<Finding> evaluate(RiskInput input) {
        Objects.requireNonNull(input);
        for (var check : checks) {
            var finding = Objects.requireNonNull(check.evaluate(input));
            if (finding.isPresent()) return finding;
        }
        return Optional.empty();
    }
    Decision decide(RiskInput input) {
        return evaluate(input).map(Finding::decision).orElse(Decision.CLEAR);
    }
}
static RiskCheck blockedAccount() {
    return input -> input.accountBlocked()
            ? Optional.of(new Finding(Decision.REJECT, "blocked-account"))
            : Optional.empty();
}
static RiskCheck highValue(long thresholdCents) {
    if (thresholdCents < 0) throw new IllegalArgumentException("Negative threshold");
    return input -> input.totalCents() >= thresholdCents
            ? Optional.of(new Finding(Decision.REVIEW, "high-value"))
            : Optional.empty();
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var chain = new RiskChain(List.of(blockedAccount(), highValue(10000)));
check(chain.decide(new RiskInput(false, 9999)) == Decision.CLEAR);
check(chain.decide(new RiskInput(false, 10000)) == Decision.REVIEW);
check(chain.decide(new RiskInput(true, 15000)) == Decision.REJECT);
int[] laterCalls = {0};
var stopped = new RiskChain(List.of(blockedAccount(), input -> {
    laterCalls[0]++;
    return Optional.empty();
}));
check(stopped.evaluate(new RiskInput(true, 10)).orElseThrow().ruleId().equals("blocked-account"));
check(laterCalls[0] == 0);
expect(IllegalArgumentException.class, () -> new RiskChain(List.of()));
expect(NullPointerException.class, () -> new RiskChain(List.of(i -> null)).decide(new RiskInput(false, 1)));
expect(IllegalArgumentException.class, () -> new Finding(Decision.CLEAR, "x"));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

