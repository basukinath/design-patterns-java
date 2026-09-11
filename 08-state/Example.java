import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  enum Status {
    NEW,
    AUTHORIZED,
    CAPTURED,
    CANCELLED
  }

  interface Phase {
    Status status();

    default Phase authorize() {
      throw new IllegalStateException("Cannot authorize " + status());
    }

    default Phase capture() {
      throw new IllegalStateException("Cannot capture " + status());
    }

    default Phase cancel() {
      throw new IllegalStateException("Cannot cancel " + status());
    }
  }

  enum NewPayment implements Phase {
    INSTANCE;

    public Status status() {
      return Status.NEW;
    }

    public Phase authorize() {
      return Authorized.INSTANCE;
    }

    public Phase cancel() {
      return Cancelled.INSTANCE;
    }
  }

  enum Authorized implements Phase {
    INSTANCE;

    public Status status() {
      return Status.AUTHORIZED;
    }

    public Phase capture() {
      return Captured.INSTANCE;
    }

    public Phase cancel() {
      return Cancelled.INSTANCE;
    }
  }

  enum Captured implements Phase {
    INSTANCE;

    public Status status() {
      return Status.CAPTURED;
    }
  }

  enum Cancelled implements Phase {
    INSTANCE;

    public Status status() {
      return Status.CANCELLED;
    }
  }

  static final class Payment {
    private Phase phase;

    Payment(Status persistedStatus) {
      phase =
          switch (Objects.requireNonNull(persistedStatus)) {
            case NEW -> NewPayment.INSTANCE;
            case AUTHORIZED -> Authorized.INSTANCE;
            case CAPTURED -> Captured.INSTANCE;
            case CANCELLED -> Cancelled.INSTANCE;
          };
    }

    Status status() {
      return phase.status();
    }

    void authorize() {
      phase = phase.authorize();
    }

    void capture() {
      phase = phase.capture();
    }

    void cancel() {
      phase = phase.cancel();
    }
  }

  private static int checks;

  static void check(boolean ok) {
    if (!ok) throw new AssertionError("Check " + (checks + 1));
    checks++;
  }

  @FunctionalInterface
  interface Throwing {
    void run() throws Exception;
  }

  static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
    try {
      action.run();
    } catch (Throwable t) {
      if (!type.isInstance(t)) throw new AssertionError(t);
      checks++;
      return;
    }
    throw new AssertionError("Expected " + type.getSimpleName());
  }

  public static void main(String[] args) throws Exception {
    var payment = new Payment(Status.NEW);
    expect(IllegalStateException.class, payment::capture);
    check(payment.status() == Status.NEW);
    payment.authorize();
    check(payment.status() == Status.AUTHORIZED);
    payment.capture();
    check(payment.status() == Status.CAPTURED);
    expect(IllegalStateException.class, payment::cancel);
    expect(IllegalStateException.class, payment::capture);
    check(payment.status() == Status.CAPTURED);
    var cancelled = new Payment(Status.AUTHORIZED);
    cancelled.cancel();
    check(cancelled.status() == Status.CANCELLED);
    expect(IllegalStateException.class, cancelled::authorize);
    var second = new Payment(Status.NEW);
    check(second.status() == Status.NEW);
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
