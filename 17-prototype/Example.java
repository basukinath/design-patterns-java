import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  record Column(String key, String heading) {
    Column {
      Objects.requireNonNull(key);
      Objects.requireNonNull(heading);
      if (key.isBlank() || heading.isBlank()) throw new IllegalArgumentException("Blank column");
    }
  }

  static final class ReportDraft {
    private final UUID id;
    private final String title;
    private final List<Column> columns;
    private final Map<String, String> filters;

    ReportDraft(UUID id, String title, List<Column> columns, Map<String, String> filters) {
      this.id = Objects.requireNonNull(id);
      this.title = Objects.requireNonNull(title);
      if (title.isBlank()) throw new IllegalArgumentException("Blank title");
      this.columns = new ArrayList<>(List.copyOf(columns));
      this.filters = new HashMap<>(Map.copyOf(filters));
    }

    ReportDraft duplicate(UUID newId, String newTitle) {
      if (id.equals(newId)) throw new IllegalArgumentException("A duplicate needs a new id");
      return new ReportDraft(newId, newTitle, columns, filters);
    }

    void addColumn(Column column) {
      columns.add(Objects.requireNonNull(column));
    }

    void setFilter(String key, String value) {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);
      if (key.isBlank()) throw new IllegalArgumentException("Blank filter key");
      filters.put(key, value);
    }

    UUID id() {
      return id;
    }

    String title() {
      return title;
    }

    List<Column> columns() {
      return List.copyOf(columns);
    }

    Map<String, String> filters() {
      return Map.copyOf(filters);
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
    var columns = new ArrayList<>(List.of(new Column("net", "Net revenue")));
    var filters = new HashMap<>(Map.of("region", "EU"));
    var source = new ReportDraft(UUID.randomUUID(), "Template", columns, filters);
    columns.clear();
    filters.clear();
    check(source.columns().size() == 1 && source.filters().get("region").equals("EU"));
    var copy = source.duplicate(UUID.randomUUID(), "October");
    check(!copy.id().equals(source.id()) && copy.title().equals("October"));
    copy.setFilter("region", "APAC");
    copy.addColumn(new Column("tax", "Tax"));
    check(source.filters().get("region").equals("EU"));
    check(source.columns().size() == 1 && copy.columns().size() == 2);
    source.setFilter("region", "US");
    check(copy.filters().get("region").equals("APAC"));
    expect(IllegalArgumentException.class, () -> source.duplicate(source.id(), "Bad"));
    expect(UnsupportedOperationException.class, () -> copy.filters().put("region", "Other"));
    check(copy.columns().get(0) == source.columns().get(0));
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
