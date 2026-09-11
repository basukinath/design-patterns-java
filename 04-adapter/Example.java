import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  record Shipment(String requestId, String postcode, long weightGrams) {
    Shipment {
      Objects.requireNonNull(requestId);
      Objects.requireNonNull(postcode);
      if (requestId.isBlank() || postcode.isBlank() || weightGrams <= 0)
        throw new IllegalArgumentException("Invalid shipment");
    }
  }

  record Booking(String trackingNumber) {
    Booking {
      Objects.requireNonNull(trackingNumber);
      if (trackingNumber.isBlank()) throw new IllegalArgumentException("Missing tracking");
    }
  }

  interface ShippingGateway {
    Booking book(Shipment shipment) throws IOException;
  }

  // Illustrative external SDK types: confined to the integration boundary.
  record ProviderRequest(String key, String destination, int grams) {}

  record ProviderReply(String status, String tracking) {}

  interface ProviderClient {
    ProviderReply book(ProviderRequest request) throws IOException;
  }

  static final class CarrierAdapter implements ShippingGateway {
    private final ProviderClient client;

    CarrierAdapter(ProviderClient client) {
      this.client = Objects.requireNonNull(client);
    }

    public Booking book(Shipment shipment) throws IOException {
      Objects.requireNonNull(shipment);
      int grams = Math.toIntExact(shipment.weightGrams());
      var reply =
          Objects.requireNonNull(
              client.book(new ProviderRequest(shipment.requestId(), shipment.postcode(), grams)),
              "Provider returned no reply");
      if (!"ACCEPTED".equals(reply.status()))
        throw new IOException("Booking not confirmed; provider status: " + reply.status());
      if (reply.tracking() == null || reply.tracking().isBlank())
        throw new IOException("Accepted booking has no tracking number");
      return new Booking(reply.tracking());
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
    var sent = new ArrayList<ProviderRequest>();
    var adapter =
        new CarrierAdapter(
            request -> {
              sent.add(request);
              return new ProviderReply("ACCEPTED", "TRACK-17");
            });
    var shipment = new Shipment("order-17-booking-1", "560001", 1250);
    check(adapter.book(shipment).trackingNumber().equals("TRACK-17"));
    check(sent.get(0).grams() == 1250);
    check(sent.get(0).key().equals(shipment.requestId()));
    expect(
        ArithmeticException.class, () -> adapter.book(new Shipment("x", "560001", Long.MAX_VALUE)));
    check(sent.size() == 1);
    expect(
        IOException.class,
        () -> new CarrierAdapter(r -> new ProviderReply("NEW_STATUS", "x")).book(shipment));
    expect(
        IOException.class,
        () -> new CarrierAdapter(r -> new ProviderReply("ACCEPTED", "")).book(shipment));
    expect(IllegalArgumentException.class, () -> new Shipment("x", "", 0));
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
