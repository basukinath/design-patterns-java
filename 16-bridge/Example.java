import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record Message(String subject, String body) {
    Message {
        Objects.requireNonNull(subject);
        Objects.requireNonNull(body);
        if (subject.isBlank() || body.isBlank())
            throw new IllegalArgumentException("Empty message");
    }
}
record Recipient(UUID accountId) {
    Recipient { Objects.requireNonNull(accountId); }
}
interface DeliveryChannel {
    void deliver(Recipient recipient, Message message);
}
abstract static class Notice {
    private final DeliveryChannel channel;
    Notice(DeliveryChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }
    public final void sendTo(Recipient recipient) {
        channel.deliver(Objects.requireNonNull(recipient), message());
    }
    protected abstract Message message();
}
static final class ReceiptNotice extends Notice {
    private final UUID orderId;
    ReceiptNotice(UUID orderId, DeliveryChannel channel) {
        super(channel);
        this.orderId = Objects.requireNonNull(orderId);
    }
    protected Message message() {
        return new Message("Order receipt", "Receipt for order " + orderId);
    }
}
static final class ReviewReminder extends Notice {
    private final String documentTitle;
    ReviewReminder(String documentTitle, DeliveryChannel channel) {
        super(channel);
        this.documentTitle = Objects.requireNonNull(documentTitle);
        if (documentTitle.isBlank()) throw new IllegalArgumentException("Blank title");
    }
    protected Message message() {
        return new Message("Review requested", "Please review: " + documentTitle);
    }
}
record Delivery(Recipient recipient, Message message) {}
static final class RecordingChannel implements DeliveryChannel {
    private final List<Delivery> deliveries = new ArrayList<>();
    public void deliver(Recipient recipient, Message message) {
        deliveries.add(new Delivery(recipient, message));
    }
    List<Delivery> deliveries() { return List.copyOf(deliveries); }
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var recipient = new Recipient(UUID.randomUUID());
var email = new RecordingChannel();
var inbox = new RecordingChannel();
var orderId = UUID.randomUUID();
new ReceiptNotice(orderId, email).sendTo(recipient);
new ReceiptNotice(orderId, inbox).sendTo(recipient);
check(email.deliveries().get(0).equals(inbox.deliveries().get(0)));
new ReviewReminder("Risk report", email).sendTo(recipient);
check(email.deliveries().get(1).message().body().equals("Please review: Risk report"));
check(inbox.deliveries().size() == 1);
check(email.deliveries().get(0).recipient().equals(recipient));
expect(IllegalArgumentException.class, () -> new ReviewReminder(" ", email));
expect(NullPointerException.class, () -> new ReceiptNotice(orderId, null));
DeliveryChannel failing = (to, message) -> { throw new IllegalStateException("Unavailable"); };
expect(IllegalStateException.class, () -> new ReceiptNotice(orderId, failing).sendTo(recipient));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

