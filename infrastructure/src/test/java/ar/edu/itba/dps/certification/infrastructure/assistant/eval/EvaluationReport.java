package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scorecard.Run;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scorecard.Totals;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scoring.CaseScore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes what an evaluation found, in Spanish for the team and the presentation: a Markdown report
 * with the metrics, a table per case and every mistake with what the model wrote, the same data as
 * JSON, and a table comparing prompt versions and models side by side.
 */
public final class EvaluationReport {

    private static final ObjectMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private enum Unit { PERCENT, COUNT }

    private EvaluationReport() {
    }

    /** Writes {@code <prompt>__<model>.md} and {@code .json} into {@code folder}; returns the Markdown file. */
    public static Path write(Path folder, Scorecard scorecard) {
        String name = fileName(scorecard);
        writeText(folder.resolve(name + ".json"), json(scorecard));
        return writeText(folder.resolve(name + ".md"), markdown(scorecard));
    }

    public static Path writeComparison(Path folder, List<Scorecard> scorecards) {
        return writeText(folder.resolve("comparacion.md"), comparison(scorecards));
    }

    public static String fileName(Scorecard scorecard) {
        return (scorecard.prompt() + "__" + scorecard.model()).replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    public static String markdown(Scorecard scorecard) {
        Totals totals = scorecard.totals();
        StringBuilder text = new StringBuilder();
        text.append("# Evaluación: `").append(scorecard.prompt()).append("` con `").append(scorecard.model()).append("`\n\n");
        text.append("- Fecha: ").append(scorecard.ranAt()).append('\n');
        text.append("- Casos: ").append(caseIds(scorecard).size()).append(" · repeticiones: ")
                .append(scorecard.repetitions()).append(" · corridas: ").append(totals.runs())
                .append(" (fallidas: ").append(totals.failedRuns()).append(")\n\n");
        text.append("## Métricas\n\n");
        text.append("| Métrica | Total | Por repetición (mín – máx) |\n|---|---|---|\n");
        metric(text, scorecard, "Hallazgos correctos (de los esperados)",
                totals.correct() + " de " + totals.expected() + " (" + percent(totals.recall()) + ")",
                Totals::recall, Unit.PERCENT);
        metric(text, scorecard, "Precisión (correctos de lo sugerido)",
                totals.correct() + " de " + totals.suggested() + " (" + percent(totals.precision()) + ")",
                Totals::precision, Unit.PERCENT);
        metric(text, scorecard, "Severidades acertadas (de los correctos)",
                totals.rightSeverity() + " de " + totals.correct() + " (" + percent(totals.severityAccuracy()) + ")",
                Totals::severityAccuracy, Unit.PERCENT);
        metric(text, scorecard, "Hallazgos inventados",
                totals.invented() + " (" + decimal(totals.inventedPerRun()) + " por corrida)",
                Totals::inventedPerRun, Unit.COUNT);
        metric(text, scorecard, "Casos sin hallazgos respondidos sin inventar",
                totals.cleanNegativeRuns() + " de " + totals.negativeRuns() + " (" + percent(totals.cleanNegatives()) + ")",
                Totals::cleanNegatives, Unit.PERCENT);
        text.append("| Resultado equivocado | ").append(totals.wrongResult()).append(" | |\n");
        text.append("| Omitidos | ").append(totals.missed()).append(" | |\n\n");
        text.append("## Por caso (sumado sobre las repeticiones)\n\n");
        text.append("| Caso | Esperados | Correctos | Sev. acertada | Res. equivocado | Inventados | Omitidos | Fallidas |\n");
        text.append("|---|---|---|---|---|---|---|---|\n");
        for (String caseId : caseIds(scorecard)) {
            List<Run> runs = scorecard.runs().stream().filter(run -> run.caseId().equals(caseId)).toList();
            List<CaseScore> scores = runs.stream().flatMap(run -> run.score().stream()).toList();
            text.append("| ").append(caseId)
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::expected).sum())
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::correct).sum())
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::rightSeverity).sum())
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::wrongResult).sum())
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::invented).sum())
                    .append(" | ").append(scores.stream().mapToInt(CaseScore::missed).sum())
                    .append(" | ").append(runs.stream().filter(run -> run.failure().isPresent()).count())
                    .append(" |\n");
        }
        text.append("\n## Errores del asistente\n\n");
        boolean any = false;
        for (Run run : scorecard.runs()) {
            List<String> lines = new ArrayList<>(run.score().map(CaseScore::mistakes).orElse(List.of()));
            run.failure().ifPresent(failure -> lines.add("no respondió: " + failure));
            for (String line : lines) {
                any = true;
                text.append("- **").append(run.caseId()).append("** (repetición ").append(run.repetition()).append("): ")
                        .append(line).append('\n');
            }
        }
        if (!any) {
            text.append("Ninguno.\n");
        }
        return text.toString();
    }

    public static String comparison(List<Scorecard> scorecards) {
        StringBuilder text = new StringBuilder("# Comparación de versiones de prompt y modelos\n\n");
        text.append("| Prompt | Modelo | Correctos | Precisión | Severidad | Inventados por corrida | Negativos limpios | Fallidas |\n");
        text.append("|---|---|---|---|---|---|---|---|\n");
        for (Scorecard scorecard : scorecards) {
            Totals totals = scorecard.totals();
            text.append("| `").append(scorecard.prompt()).append("` | `").append(scorecard.model())
                    .append("` | ").append(percent(totals.recall()))
                    .append(" | ").append(percent(totals.precision()))
                    .append(" | ").append(percent(totals.severityAccuracy()))
                    .append(" | ").append(decimal(totals.inventedPerRun()))
                    .append(" | ").append(percent(totals.cleanNegatives()))
                    .append(" | ").append(totals.failedRuns()).append(" de ").append(totals.runs())
                    .append(" |\n");
        }
        return text.toString();
    }

    public static String json(Scorecard scorecard) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("prompt", scorecard.prompt());
        root.put("model", scorecard.model());
        root.put("repetitions", scorecard.repetitions());
        root.put("ranAt", scorecard.ranAt().toString());
        Totals totals = scorecard.totals();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expected", totals.expected());
        summary.put("suggested", totals.suggested());
        summary.put("correct", totals.correct());
        summary.put("rightSeverity", totals.rightSeverity());
        summary.put("wrongResult", totals.wrongResult());
        summary.put("invented", totals.invented());
        summary.put("missed", totals.missed());
        summary.put("runs", totals.runs());
        summary.put("failedRuns", totals.failedRuns());
        summary.put("negativeRuns", totals.negativeRuns());
        summary.put("cleanNegativeRuns", totals.cleanNegativeRuns());
        root.put("totals", summary);
        List<Map<String, Object>> runs = new ArrayList<>();
        for (Run run : scorecard.runs()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("case", run.caseId());
            line.put("repetition", run.repetition());
            line.put("millis", run.took().toMillis());
            run.score().ifPresent(score -> {
                line.put("expected", score.expected());
                line.put("suggested", score.suggested());
                line.put("correct", score.correct());
                line.put("rightSeverity", score.rightSeverity());
                line.put("wrongResult", score.wrongResult());
                line.put("invented", score.invented());
                line.put("missed", score.missed());
                line.put("mistakes", score.mistakes());
            });
            run.failure().ifPresent(failure -> line.put("failure", failure));
            runs.add(line);
        }
        root.put("runs", runs);
        return JSON.writeValueAsString(root) + "\n";
    }

    private static void metric(StringBuilder text, Scorecard scorecard, String name, String total,
            ToDoubleFunction<Totals> ratio, Unit unit) {
        DoubleSummaryStatistics byRepetition = IntStream.rangeClosed(1, scorecard.repetitions())
                .mapToDouble(repetition -> ratio.applyAsDouble(scorecard.totals(repetition)))
                .filter(value -> !Double.isNaN(value))
                .summaryStatistics();
        String range = byRepetition.getCount() == 0 ? "—"
                : format(byRepetition.getMin(), unit) + " – " + format(byRepetition.getMax(), unit);
        text.append("| ").append(name).append(" | ").append(total).append(" | ").append(range).append(" |\n");
    }

    private static String format(double value, Unit unit) {
        return unit == Unit.COUNT ? decimal(value) : percent(value);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%.0f %%", value * 100);
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static List<String> caseIds(Scorecard scorecard) {
        return scorecard.runs().stream().map(Run::caseId).distinct().toList();
    }

    private static Path writeText(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}
