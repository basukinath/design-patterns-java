import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FactoryChecks {
  private static int checks;

  public static void main(String[] args) throws Exception {
    var report =
        new ReportExports.Report(List.of("Order", "Total"), List.of(List.of("ORD-1042", "125.00")));
    var pdfOptions = new ReportExports.PdfOptions("monthly-summary");
    var excelOptions = new ReportExports.ExcelOptions(true);
    var calls = new ArrayList<String>();
    var output = new TrackingOutput();
    ReportExports.PdfRenderer pdf =
        (actual, options, target) -> {
          check(
              actual == report && options.equals(pdfOptions) && target == output,
              "PDF receives report, configured options and caller output");
          calls.add("PDF");
        };
    ReportExports.ExcelRenderer excel =
        (actual, options, target) -> {
          check(
              actual == report && options.equals(excelOptions) && target == output,
              "Excel receives report, configured options and caller output");
          calls.add("EXCEL");
        };
    var factory = new ReportExports.ReportExporterFactory(pdf, excel, pdfOptions, excelOptions);
    var service = new ReportExports.ReportExportService(factory);
    service.export(report, ReportExports.ExportFormat.PDF, output);
    service.export(report, ReportExports.ExportFormat.EXCEL, output);
    check(calls.equals(List.of("PDF", "EXCEL")), "Correct renderer selected");
    service.export(report, ReportExports.ExportFormat.CSV, output);
    check(calls.size() == 2, "CSV does not invoke either document renderer");
    check(
        output
            .toString(StandardCharsets.UTF_8)
            .equals("\"Order\",\"Total\"\r\n\"ORD-1042\",\"125.00\"\r\n"),
        "CSV document");
    check(!output.closed, "Caller stream remains open");
    check(
        factory.create(ReportExports.ExportFormat.PDF)
            != factory.create(ReportExports.ExportFormat.PDF),
        "Fresh wrapper each call");
    expect(NullPointerException.class, () -> factory.create(null));

    var special =
        new ReportExports.Report(
            List.of("Text"),
            List.of(
                List.of("A, B"),
                List.of("A \"quote\""),
                List.of("line\nnext"),
                List.of(""),
                List.of("Basu — café")));
    var csvOutput = new TrackingOutput();
    new ReportExports.CsvReportExporter().export(special, csvOutput);
    check(
        csvOutput
            .toString(StandardCharsets.UTF_8)
            .equals(
                "\"Text\"\r\n\"A, B\"\r\n\"A \"\"quote\"\"\"\r\n"
                    + "\"line\nnext\"\r\n\"\"\r\n\"Basu — café\"\r\n"),
        "CSV preserves commas, quotes, newlines, empty strings and Unicode");
    check(!csvOutput.closed, "CSV does not close output");

    var row = new ArrayList<>(List.of("original"));
    var rows = new ArrayList<List<String>>();
    rows.add(row);
    var columns = new ArrayList<>(List.of("Header"));
    var snapshot = new ReportExports.Report(columns, rows);
    columns.set(0, "changed");
    row.set(0, "changed");
    rows.clear();
    check(
        snapshot.columns().equals(List.of("Header"))
            && snapshot.rows().equals(List.of(List.of("original"))),
        "Report snapshots both collection levels");
    expect(UnsupportedOperationException.class, () -> snapshot.rows().get(0).add("extra"));
    expect(
        IllegalArgumentException.class,
        () -> new ReportExports.Report(List.of("A"), List.of(List.of())));
    expect(IllegalArgumentException.class, () -> new ReportExports.Report(List.of(), List.of()));
    expect(IllegalArgumentException.class, () -> new ReportExports.PdfOptions(" "));

    var failure = new IOException("destination unavailable");
    var brokenOutput =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            throw failure;
          }
        };
    try {
      service.export(report, ReportExports.ExportFormat.CSV, brokenOutput);
      throw new AssertionError("Expected destination failure");
    } catch (IOException actual) {
      check(actual == failure, "CSV failure reaches caller unchanged");
    }
    ReportExports.PdfRenderer failingPdf =
        (r, options, target) -> {
          throw failure;
        };
    var failingFactory =
        new ReportExports.ReportExporterFactory(failingPdf, excel, pdfOptions, excelOptions);
    try {
      failingFactory.create(ReportExports.ExportFormat.PDF).export(report, output);
      throw new AssertionError("Expected renderer failure");
    } catch (IOException actual) {
      check(actual == failure, "Renderer failure reaches caller unchanged");
    }
    System.out.println("PASS: " + checks + " behavioral checks");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }

  @FunctionalInterface
  private interface CheckedAction {
    void run() throws Exception;
  }

  private static void expect(Class<? extends Throwable> type, CheckedAction action)
      throws Exception {
    try {
      action.run();
    } catch (Throwable actual) {
      if (!type.isInstance(actual)) throw new AssertionError("Wrong exception", actual);
      checks++;
      return;
    }
    throw new AssertionError("Expected " + type.getSimpleName());
  }

  private static final class TrackingOutput extends ByteArrayOutputStream {
    private boolean closed;

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
