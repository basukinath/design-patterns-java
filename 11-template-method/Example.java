import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
abstract static class ImportJob<T> {
    private final Consumer<List<T>> sink;
    ImportJob(Consumer<List<T>> sink) { this.sink = Objects.requireNonNull(sink); }

    final int run(Reader source) throws IOException {
        Objects.requireNonNull(source);
        var reader = source instanceof BufferedReader buffered
                ? buffered : new BufferedReader(source);
        var parsed = new ArrayList<T>();
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            if (line.isBlank()) continue;
            try { parsed.add(Objects.requireNonNull(parse(line))); }
            catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Invalid line " + number, failure);
            }
        }
        if (!parsed.isEmpty()) sink.accept(List.copyOf(parsed));
        return parsed.size();
    }
    protected abstract T parse(String line);
}
static String[] fields(String line) {
    String[] fields = line.split("\\|", -1);
    if (fields.length != 2 || fields[0].isBlank())
        throw new IllegalArgumentException("Expected SKU|value");
    return fields;
}
record StockEntry(String sku, int quantity) {
    StockEntry {
        if (quantity < 0) throw new IllegalArgumentException("Negative quantity");
    }
}
record PriceEntry(String sku, long cents) {
    PriceEntry {
        if (cents < 0) throw new IllegalArgumentException("Negative price");
    }
}
static final class StockImport extends ImportJob<StockEntry> {
    StockImport(Consumer<List<StockEntry>> sink) { super(sink); }
    protected StockEntry parse(String line) {
        var value = fields(line);
        return new StockEntry(value[0], Integer.parseInt(value[1]));
    }
}
static final class PriceImport extends ImportJob<PriceEntry> {
    PriceImport(Consumer<List<PriceEntry>> sink) { super(sink); }
    protected PriceEntry parse(String line) {
        var value = fields(line);
        return new PriceEntry(value[0], Long.parseLong(value[1]));
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
var saved = new ArrayList<List<StockEntry>>();
var job = new StockImport(saved::add);
boolean[] closed = {false};
var reader = new StringReader("sku-1|2\n\nsku-2|3") {
    @Override public void close() { closed[0] = true; super.close(); }
};
check(job.run(reader) == 2);
check(saved.get(0).get(1).quantity() == 3 && !closed[0]);
expect(UnsupportedOperationException.class, () -> saved.get(0).clear());
saved.clear();
expect(IllegalArgumentException.class, () -> job.run(new StringReader("sku-1|2\nbad")));
check(saved.isEmpty());
check(job.run(new StringReader("")) == 0 && saved.isEmpty());
var prices = new ArrayList<PriceEntry>();
new PriceImport(prices::addAll).run(new StringReader("sku-1|2500"));
check(prices.get(0).cents() == 2500);
expect(IllegalArgumentException.class, () -> job.run(new StringReader("sku-1|-1")));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

