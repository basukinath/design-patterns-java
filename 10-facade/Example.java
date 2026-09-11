import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  record Line(String sku, int quantity) {
    Line {
      Objects.requireNonNull(sku);
      if (sku.isBlank() || quantity < 1) throw new IllegalArgumentException("Invalid line");
    }
  }

  record Cart(List<Line> lines) {
    Cart {
      lines = List.copyOf(lines);
      if (lines.isEmpty()) throw new IllegalArgumentException("Empty cart");
      var skus = new HashSet<String>();
      for (var line : lines)
        if (!skus.add(line.sku())) throw new IllegalArgumentException("Duplicate SKU");
    }
  }

  interface Catalog {
    long unitPrice(String sku);
  }

  interface Inventory {
    boolean available(String sku, int quantity);
  }

  interface Shipping {
    long fee(Cart cart, long subtotalCents);
  }

  record Preview(
      long subtotalCents, long shippingCents, long totalCents, List<String> unavailableSkus) {
    Preview {
      unavailableSkus = List.copyOf(unavailableSkus);
    }
  }

  record CheckoutFacade(Catalog catalog, Inventory inventory, Shipping shipping) {
    CheckoutFacade {
      Objects.requireNonNull(catalog);
      Objects.requireNonNull(inventory);
      Objects.requireNonNull(shipping);
    }

    Preview preview(Cart cart) {
      Objects.requireNonNull(cart);
      long subtotal = 0;
      var unavailable = new ArrayList<String>();
      for (var line : cart.lines()) {
        long price = catalog.unitPrice(line.sku());
        if (price < 0) throw new IllegalStateException("Negative catalog price");
        subtotal = Math.addExact(subtotal, Math.multiplyExact(price, line.quantity()));
        if (!inventory.available(line.sku(), line.quantity())) unavailable.add(line.sku());
      }
      long fee = shipping.fee(cart, subtotal);
      if (fee < 0) throw new IllegalStateException("Negative shipping fee");
      return new Preview(subtotal, fee, Math.addExact(subtotal, fee), unavailable);
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
    var cart = new Cart(List.of(new Line("sku-a", 2), new Line("sku-b", 1)));
    var facade =
        new CheckoutFacade(
            s -> s.equals("sku-a") ? 1000 : 500, (s, q) -> s.equals("sku-a"), (c, subtotal) -> 200);
    var preview = facade.preview(cart);
    check(preview.subtotalCents() == 2500 && preview.totalCents() == 2700);
    check(preview.unavailableSkus().equals(List.of("sku-b")));
    expect(UnsupportedOperationException.class, () -> preview.unavailableSkus().clear());
    expect(
        IllegalArgumentException.class,
        () -> new Cart(List.of(new Line("x", 1), new Line("x", 2))));
    expect(
        IllegalStateException.class,
        () -> new CheckoutFacade(s -> -1, (s, q) -> true, (c, t) -> 0).preview(cart));
    expect(
        ArithmeticException.class,
        () -> new CheckoutFacade(s -> Long.MAX_VALUE, (s, q) -> true, (c, t) -> 0).preview(cart));
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
