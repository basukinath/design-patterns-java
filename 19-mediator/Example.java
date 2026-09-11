import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  record Submission(UUID documentId, UUID authorId) {
    Submission {
      Objects.requireNonNull(documentId);
      Objects.requireNonNull(authorId);
    }
  }

  record Review(UUID documentId, UUID authorId, UUID reviewerId) {}

  enum NotificationOutcome {
    SENT,
    FAILED
  }

  record SubmissionResult(Review review, NotificationOutcome notification) {}

  interface ReviewerDirectory {
    UUID chooseReviewer(Submission submission);
  }

  interface ReviewStore {
    Review createOrGet(Submission submission, UUID reviewerId);
  }

  interface ReviewNotifications {
    void send(Review review) throws IOException;
  }

  static final class ReviewCoordinator {
    private final ReviewerDirectory directory;
    private final ReviewStore store;
    private final ReviewNotifications notifications;

    ReviewCoordinator(
        ReviewerDirectory directory, ReviewStore store, ReviewNotifications notifications) {
      this.directory = Objects.requireNonNull(directory);
      this.store = Objects.requireNonNull(store);
      this.notifications = Objects.requireNonNull(notifications);
    }

    SubmissionResult submit(Submission submission) {
      var reviewer = Objects.requireNonNull(directory.chooseReviewer(submission));
      if (reviewer.equals(submission.authorId()))
        throw new IllegalArgumentException("Author cannot review own document");
      var review = store.createOrGet(submission, reviewer);
      try {
        notifications.send(review);
        return new SubmissionResult(review, NotificationOutcome.SENT);
      } catch (IOException failure) {
        return new SubmissionResult(review, NotificationOutcome.FAILED);
      }
    }
  }

  static final class MemoryReviews implements ReviewStore {
    private final Map<UUID, Review> reviews = new HashMap<>();

    public synchronized Review createOrGet(Submission submission, UUID reviewerId) {
      var existing = reviews.get(submission.documentId());
      if (existing != null) {
        if (!existing.authorId().equals(submission.authorId()))
          throw new IllegalArgumentException("Submission identity conflict");
        return existing;
      }
      var review = new Review(submission.documentId(), submission.authorId(), reviewerId);
      reviews.put(submission.documentId(), review);
      return review;
    }

    synchronized int size() {
      return reviews.size();
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
    var author = UUID.randomUUID();
    var reviewer = UUID.randomUUID();
    var submission = new Submission(UUID.randomUUID(), author);
    var reviews = new MemoryReviews();
    var sent = new ArrayList<Review>();
    var coordinator = new ReviewCoordinator(s -> reviewer, reviews, sent::add);
    var first = coordinator.submit(submission);
    check(first.notification() == NotificationOutcome.SENT);
    check(first.review().reviewerId().equals(reviewer) && reviews.size() == 1);
    check(coordinator.submit(submission).review().equals(first.review()) && reviews.size() == 1);
    check(sent.size() == 2);
    var unavailable =
        new ReviewCoordinator(
            s -> reviewer,
            reviews,
            r -> {
              throw new IOException("Offline");
            });
    check(unavailable.submit(submission).notification() == NotificationOutcome.FAILED);
    check(reviews.size() == 1);
    expect(
        IllegalArgumentException.class,
        () -> new ReviewCoordinator(s -> author, reviews, sent::add).submit(submission));
    expect(
        IllegalArgumentException.class,
        () -> coordinator.submit(new Submission(submission.documentId(), UUID.randomUUID())));
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
