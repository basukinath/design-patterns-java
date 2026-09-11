import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Java 17-compatible design example. PDF/XLSX engines are application ports. */
public final class ReportExports {
  private ReportExports() {}

  public enum ExportFormat {
    PDF,
    CSV,
    EXCEL
  }

  public record Report(List<String> columns, List<List<String>> rows) {
    public Report {
      columns = List.copyOf(columns);
      if (columns.isEmpty()) {
        throw new IllegalArgumentException("A report needs columns");
      }
      rows = rows.stream().map(List::copyOf).toList();
      for (var row : rows) {
        if (row.size() != columns.size()) {
          throw new IllegalArgumentException("Row width differs from columns");
        }
      }
    }
  }

  public record PdfOptions(String templateName) {
    public PdfOptions {
      Objects.requireNonNull(templateName, "templateName");
      if (templateName.isBlank()) {
        throw new IllegalArgumentException("Template name is blank");
      }
    }
  }

  public record ExcelOptions(boolean freezeHeader) {}

  public interface ReportExporter {
    /** Writes a document, propagates I/O failures, and leaves target open. */
    void export(Report report, OutputStream target) throws IOException;
  }

  @FunctionalInterface
  public interface PdfRenderer {
    /** Implementations must leave target open and manage document resources. */
    void render(Report report, PdfOptions options, OutputStream target) throws IOException;
  }

  @FunctionalInterface
  public interface ExcelRenderer {
    /** Write XLSX, not CSV with an .xlsx suffix. Leave target open. */
    void render(Report report, ExcelOptions options, OutputStream target) throws IOException;
  }

  public static final class PdfReportExporter implements ReportExporter {
    private final PdfRenderer renderer;
    private final PdfOptions options;

    public PdfReportExporter(PdfRenderer renderer, PdfOptions options) {
      this.renderer = Objects.requireNonNull(renderer, "renderer");
      this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public void export(Report report, OutputStream target) throws IOException {
      renderer.render(
          Objects.requireNonNull(report, "report"),
          options,
          Objects.requireNonNull(target, "target"));
    }
  }

  public static final class ExcelReportExporter implements ReportExporter {
    private final ExcelRenderer renderer;
    private final ExcelOptions options;

    public ExcelReportExporter(ExcelRenderer renderer, ExcelOptions options) {
      this.renderer = Objects.requireNonNull(renderer, "renderer");
      this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public void export(Report report, OutputStream target) throws IOException {
      renderer.render(
          Objects.requireNonNull(report, "report"),
          options,
          Objects.requireNonNull(target, "target"));
    }
  }

  public static final class CsvReportExporter implements ReportExporter {
    @Override
    public void export(Report report, OutputStream target) throws IOException {
      Objects.requireNonNull(report, "report");
      Objects.requireNonNull(target, "target");
      writeRow(report.columns(), target);
      for (var row : report.rows()) {
        writeRow(row, target);
      }
    }

    private static void writeRow(List<String> cells, OutputStream target) throws IOException {
      for (int i = 0; i < cells.size(); i++) {
        if (i > 0) target.write(',');
        String quoted = "\"" + cells.get(i).replace("\"", "\"\"") + "\"";
        target.write(quoted.getBytes(StandardCharsets.UTF_8));
      }
      target.write('\r');
      target.write('\n');
    }
  }

  public static final class ReportExporterFactory {
    private final PdfRenderer pdfRenderer;
    private final ExcelRenderer excelRenderer;
    private final PdfOptions pdfOptions;
    private final ExcelOptions excelOptions;

    public ReportExporterFactory(
        PdfRenderer pdfRenderer,
        ExcelRenderer excelRenderer,
        PdfOptions pdfOptions,
        ExcelOptions excelOptions) {
      this.pdfRenderer = Objects.requireNonNull(pdfRenderer, "pdfRenderer");
      this.excelRenderer = Objects.requireNonNull(excelRenderer, "excelRenderer");
      this.pdfOptions = Objects.requireNonNull(pdfOptions, "pdfOptions");
      this.excelOptions = Objects.requireNonNull(excelOptions, "excelOptions");
    }

    public ReportExporter create(ExportFormat format) {
      Objects.requireNonNull(format, "format");
      return switch (format) {
        case PDF -> new PdfReportExporter(pdfRenderer, pdfOptions);
        case CSV -> new CsvReportExporter();
        case EXCEL -> new ExcelReportExporter(excelRenderer, excelOptions);
      };
    }
  }

  public static final class ReportExportService {
    private final ReportExporterFactory exporters;

    public ReportExportService(ReportExporterFactory exporters) {
      this.exporters = Objects.requireNonNull(exporters, "exporters");
    }

    public void export(Report report, ExportFormat format, OutputStream target) throws IOException {
      exporters.create(format).export(report, target);
    }
  }

  public static void main(String[] args) throws IOException {
    var report = new Report(List.of("Order", "Total"), List.of(List.of("ORD-1042", "125.00")));
    // The runnable demo intentionally emits genuine CSV only.
    new CsvReportExporter().export(report, System.out);
  }
}
