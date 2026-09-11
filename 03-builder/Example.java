import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  enum Format {
    CSV,
    PDF
  }

  record ExportRequest(
      String reportId,
      Format format,
      List<String> columns,
      boolean landscape,
      boolean includeTotals) {
    ExportRequest {
      Objects.requireNonNull(reportId);
      Objects.requireNonNull(format);
      if (reportId.isBlank()) throw new IllegalArgumentException("Blank report id");
      columns = List.copyOf(columns);
      if (columns.isEmpty() || columns.stream().anyMatch(String::isBlank))
        throw new IllegalArgumentException("Columns required");
      if (new HashSet<>(columns).size() != columns.size())
        throw new IllegalArgumentException("Duplicate column");
      if (format == Format.CSV && landscape)
        throw new IllegalArgumentException("CSV has no page orientation");
    }

    static Builder builder(String reportId, Format format) {
      return new Builder(reportId, format);
    }

    static final class Builder {
      private final String reportId;
      private final Format format;
      private List<String> columns = List.of("order", "total");
      private boolean landscape;
      private boolean includeTotals;

      private Builder(String reportId, Format format) {
        this.reportId = Objects.requireNonNull(reportId);
        this.format = Objects.requireNonNull(format);
      }

      Builder columns(List<String> value) {
        columns = List.copyOf(value);
        return this;
      }

      Builder landscape(boolean value) {
        landscape = value;
        return this;
      }

      Builder includeTotals(boolean value) {
        includeTotals = value;
        return this;
      }

      ExportRequest build() {
        return new ExportRequest(reportId, format, columns, landscape, includeTotals);
      }
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
    var builder = ExportRequest.builder("monthly", Format.PDF);
    var first = builder.build();
    var second = builder.landscape(true).includeTotals(true).build();
    check(!first.landscape() && second.landscape());
    check(second.includeTotals());
    var columns = new ArrayList<>(List.of("order"));
    var captured = ExportRequest.builder("monthly", Format.CSV).columns(columns);
    columns.add("customer");
    check(captured.build().columns().equals(List.of("order")));
    expect(UnsupportedOperationException.class, () -> first.columns().add("x"));
    expect(
        IllegalArgumentException.class,
        () -> ExportRequest.builder("x", Format.CSV).landscape(true).build());
    expect(
        IllegalArgumentException.class,
        () -> new ExportRequest("", Format.PDF, List.of("x"), false, false));
    expect(IllegalArgumentException.class, () -> builder.columns(List.of("x", "x")).build());
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
