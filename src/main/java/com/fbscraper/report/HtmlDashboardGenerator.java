package com.fbscraper.report;

import com.fbscraper.model.AnalyzedComment;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.SentimentLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates an interactive, self-contained HTML5/CSS3 dashboard highlighting
 * customer sentiment, KPI statistics, Page ratings, and reviews.
 */
public class HtmlDashboardGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Backward-compatible report generation method without reviews.
     */
    public void generateReport(List<FacebookPost> posts, List<AnalyzedComment> analyzedComments, Path outputPath) {
        generateReport(posts, analyzedComments, PageRatingSummary.EMPTY, List.of(), outputPath);
    }

    /**
     * Generates a comprehensive HTML dashboard report including post comments, page ratings, and reviews.
     *
     * @param posts            the list of scraped Facebook posts
     * @param analyzedComments the list of sentiment-analyzed comments
     * @param ratingSummary    overall page rating statistics (or EMPTY)
     * @param analyzedReviews  the list of customer reviews and recommendations
     * @param outputPath       the destination file path (e.g. output/dashboard.html)
     */
    public void generateReport(
            List<FacebookPost> posts,
            List<AnalyzedComment> analyzedComments,
            PageRatingSummary ratingSummary,
            List<AnalyzedReview> analyzedReviews,
            Path outputPath
    ) {
        long totalPosts = posts.size();
        long totalComments = analyzedComments.size();

        long positiveCount = analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.POSITIVE).count();
        long neutralCount = analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.NEUTRAL).count();
        long warningCount = analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.WARNING_NEGATIVE).count();
        long criticalCount = analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.CRITICAL_NEGATIVE).count();
        long totalNegative = warningCount + criticalCount;

        double negativePercent = totalComments > 0 ? ((double) totalNegative / totalComments) * 100.0 : 0.0;
        double positivePercent = totalComments > 0 ? ((double) positiveCount / totalComments) * 100.0 : 0.0;

        String healthBadge;
        String healthClass;
        if (negativePercent > 25.0) {
            healthBadge = "⚠️ Attention Required";
            healthClass = "badge-danger";
        } else if (negativePercent > 10.0) {
            healthBadge = "⚡ Needs Monitoring";
            healthClass = "badge-warning";
        } else {
            healthBadge = "✅ Healthy & Positive";
            healthClass = "badge-success";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"en\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <title>Facebook Scraper Dashboard</title>\n")
            .append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n")
            .append("  <style>\n")
            .append("    :root {\n")
            .append("      --bg-color: #0f172a;\n")
            .append("      --card-bg: #1e293b;\n")
            .append("      --card-border: #334155;\n")
            .append("      --text-main: #f8fafc;\n")
            .append("      --text-muted: #94a3b8;\n")
            .append("      --primary: #3b82f6;\n")
            .append("      --success: #10b981;\n")
            .append("      --warning: #f59e0b;\n")
            .append("      --danger: #ef4444;\n")
            .append("    }\n")
            .append("    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }\n")
            .append("    body { background-color: var(--bg-color); color: var(--text-main); padding: 2rem; line-height: 1.5; }\n")
            .append("    .container { max-width: 1200px; margin: 0 auto; }\n")
            .append("    header { margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; }\n")
            .append("    h1 { font-size: 1.875rem; font-weight: 700; color: #fff; }\n")
            .append("    .subtitle { color: var(--text-muted); font-size: 0.875rem; margin-top: 0.25rem; }\n")
            .append("    .grid-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.25rem; margin-bottom: 2rem; }\n")
            .append("    .card { background-color: var(--card-bg); border: 1px solid var(--card-border); border-radius: 0.75rem; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.2); }\n")
            .append("    .card-title { font-size: 0.875rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem; }\n")
            .append("    .card-value { font-size: 2rem; font-weight: 700; }\n")
            .append("    .grid-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-bottom: 2rem; }\n")
            .append("    @media (max-width: 768px) { .grid-charts { grid-template-columns: 1fr; } }\n")
            .append("    .chart-container { height: 280px; position: relative; display: flex; justify-content: center; align-items: center; }\n")
            .append("    .badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }\n")
            .append("    .badge-success { background-color: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.4); }\n")
            .append("    .badge-warning { background-color: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.4); }\n")
            .append("    .badge-danger { background-color: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.4); }\n")
            .append("    .badge-neutral { background-color: rgba(148, 163, 184, 0.2); color: #cbd5e1; border: 1px solid rgba(148, 163, 184, 0.4); }\n")
            .append("    .search-box { width: 100%; padding: 0.75rem 1rem; border-radius: 0.5rem; background-color: #0f172a; border: 1px solid var(--card-border); color: #fff; margin-bottom: 1.25rem; font-size: 0.95rem; }\n")
            .append("    .search-box:focus { outline: 2px solid var(--primary); }\n")
            .append("    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 0.9rem; }\n")
            .append("    th { background-color: #1e293b; color: var(--text-muted); padding: 0.75rem 1rem; border-bottom: 1px solid var(--card-border); }\n")
            .append("    td { padding: 0.85rem 1rem; border-bottom: 1px solid #334155; vertical-align: top; }\n")
            .append("    tr:hover { background-color: rgba(255,255,255,0.02); }\n")
            .append("    .comment-text { color: #f1f5f9; font-weight: 500; margin-bottom: 0.25rem; }\n")
            .append("    .post-snippet { color: var(--text-muted); font-size: 0.8rem; font-style: italic; }\n")
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("<div class=\"container\">\n")
            .append("  <header>\n")
            .append("    <div>\n")
            .append("      <h1>Facebook Scraper Dashboard</h1>\n")
            .append("      <p class=\"subtitle\">Generated on: ").append(DATE_FMT.format(java.time.Instant.now())).append("</p>\n")
            .append("    </div>\n")
            .append("    <div>\n")
            .append("      <span class=\"badge ").append(healthClass).append("\" style=\"font-size: 0.95rem; padding: 0.4rem 1rem;\">")
            .append(healthBadge).append("</span>\n")
            .append("    </div>\n")
            .append("  </header>\n\n");

        // KPI Stat Cards
        html.append("  <div class=\"grid-stats\">\n")
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Total Posts Scanned</div>\n")
            .append("      <div class=\"card-value\">").append(totalPosts).append("</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Total Comments</div>\n")
            .append("      <div class=\"card-value\">").append(totalComments).append("</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Negative Feedback Rate</div>\n")
            .append("      <div class=\"card-value\" style=\"color: ").append(negativePercent > 20 ? "var(--danger)" : "var(--text-main)").append(";\">")
            .append(String.format("%.1f%%", negativePercent)).append("</div>\n")
            .append("      <div class=\"subtitle\">").append(totalNegative).append(" flagged comments</div>\n")
            .append("    </div>\n")
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Positive Feedback Rate</div>\n")
            .append("      <div class=\"card-value\" style=\"color: var(--success);\">")
            .append(String.format("%.1f%%", positivePercent)).append("</div>\n")
            .append("      <div class=\"subtitle\">").append(positiveCount).append(" positive comments</div>\n")
            .append("    </div>\n");

        long reviewYesCount = analyzedReviews != null ? analyzedReviews.stream().filter(r -> "positive".equalsIgnoreCase(r.review().recommendationType())).count() : 0;
        long reviewNoCount = analyzedReviews != null ? analyzedReviews.stream().filter(r -> "negative".equalsIgnoreCase(r.review().recommendationType())).count() : 0;
        long totalReviews = analyzedReviews != null ? analyzedReviews.size() : 0;
        long reviewOtherCount = totalReviews - (reviewYesCount + reviewNoCount);

        boolean hasMetaRating = ratingSummary != null && ratingSummary.hasRatings();
        if (hasMetaRating || totalReviews > 0) {
            double avgRating = hasMetaRating
                    ? ratingSummary.overallStarRating()
                    : ((double) reviewYesCount / totalReviews) * 5.0;

            String ratingSubtitle;
            if (hasMetaRating && totalReviews > 0) {
                ratingSubtitle = "Overall Page Rating · " + ratingSummary.ratingCount() + " total ratings (" + reviewYesCount + " Yes, " + reviewNoCount + " No)";
            } else if (hasMetaRating) {
                ratingSubtitle = "Overall Page Rating · " + ratingSummary.ratingCount() + " total ratings";
            } else {
                ratingSubtitle = reviewYesCount + " Recommends (Yes) · " + reviewNoCount + " Doesn't Rec. (No)";
            }

            html.append("    <div class=\"card\">\n")
                .append("      <div class=\"card-title\">Average Review Rating</div>\n")
                .append("      <div class=\"card-value\" style=\"color: #fbbf24;\">⭐ ")
                .append(String.format("%.1f", avgRating))
                .append(" <span style=\"font-size: 1rem; color: var(--text-muted); font-weight: normal;\">/ 5.0</span></div>\n")
                .append("      <div class=\"subtitle\">").append(ratingSubtitle).append("</div>\n")
                .append("    </div>\n");
        }

        html.append("  </div>\n\n");

        // Charts & Insights
        html.append("  <div class=\"grid-charts\">\n")
            // Comments sentiment doughnut chart
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Comment Sentiment Breakdown</div>\n")
            .append("      <div class=\"chart-container\">\n")
            .append("        <canvas id=\"sentimentChart\"></canvas>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            // Customer reviews recommendation pie chart (reviews only)
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Review Recommendations (Yes vs No)</div>\n")
            .append("      <div class=\"chart-container\">\n");

        if (totalReviews > 0) {
            html.append("        <canvas id=\"reviewPieChart\"></canvas>\n");
        } else {
            html.append("        <div style=\"display: flex; justify-content: center; align-items: center; height: 100%; color: var(--text-muted); font-size: 0.9rem;\">No customer reviews recorded yet</div>\n");
        }

        html.append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Analysis Summary & Action Items</div>\n")
            .append("      <ul style=\"color: var(--text-muted); padding-left: 1.25rem; margin-top: 1rem; line-height: 1.8;\">\n")
            .append("        <li><strong>Critical issues:</strong> ").append(criticalCount).append(" comments scored as severe complaints. Immediate support reply recommended.</li>\n")
            .append("        <li><strong>Mild dissatisfaction:</strong> ").append(warningCount).append(" comments noted minor issues or delays.</li>\n")
            .append("        <li><strong>Positive engagement:</strong> ").append(positiveCount).append(" happy customers sharing praise or satisfaction.</li>\n")
            .append("        <li><strong>Neutral/Inquiries:</strong> ").append(neutralCount).append(" standard questions or informational queries.</li>\n");

        if (totalReviews > 0) {
            html.append("        <li><strong>Customer Reviews:</strong> ").append(reviewYesCount).append(" recommended (Yes) vs ").append(reviewNoCount).append(" did not recommend (No).</li>\n");
        }

        html.append("      </ul>\n")
            .append("    </div>\n")
            .append("  </div>\n\n");

        // Negative Comments Table
        html.append("  <div class=\"card\">\n")
            .append("    <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;\">\n")
            .append("      <div class=\"card-title\" style=\"margin-bottom: 0;\">Flagged Negative Feedback (").append(totalNegative).append(")</div>\n")
            .append("    </div>\n")
            .append("    <input type=\"text\" id=\"searchInput\" class=\"search-box\" placeholder=\"Filter comments by keyword (e.g., refund, broken, slow)...\" onkeyup=\"filterTable()\">\n")
            .append("    <table id=\"negativeTable\">\n")
            .append("      <thead>\n")
            .append("        <tr>\n")
            .append("          <th style=\"width: 110px;\">Severity</th>\n")
            .append("          <th style=\"width: 80px;\">Score</th>\n")
            .append("          <th>Comment & Related Post</th>\n")
            .append("          <th style=\"width: 170px;\">Date</th>\n")
            .append("        </tr>\n")
            .append("      </thead>\n")
            .append("      <tbody>\n");

        List<AnalyzedComment> negativeComments = analyzedComments.stream()
                .filter(c -> c.score().isNegative())
                .sorted((a, b) -> Double.compare(a.score().compound(), b.score().compound())) // Most negative first
                .toList();

        if (negativeComments.isEmpty()) {
            html.append("        <tr><td colspan=\"4\" style=\"text-align: center; color: var(--text-muted); padding: 2rem;\">🎉 No negative comments detected! Great job!</td></tr>\n");
        } else {
            for (AnalyzedComment item : negativeComments) {
                String badge = item.score().level() == SentimentLevel.CRITICAL_NEGATIVE ?
                        "<span class=\"badge badge-danger\">CRITICAL</span>" :
                        "<span class=\"badge badge-warning\">WARNING</span>";

                html.append("        <tr>\n")
                    .append("          <td>").append(badge).append("</td>\n")
                    .append("          <td><strong style=\"color: ").append(item.score().level() == SentimentLevel.CRITICAL_NEGATIVE ? "var(--danger)" : "var(--warning)").append(";\">")
                    .append(String.format("%.2f", item.score().compound())).append("</strong></td>\n")
                    .append("          <td>\n")
                    .append("            <div class=\"comment-text\">").append(escapeHtml(item.comment().message())).append("</div>\n")
                    .append("            <div class=\"post-snippet\">On post: \"").append(escapeHtml(item.postSnippet())).append("\"</div>\n")
                    .append("          </td>\n")
                    .append("          <td style=\"color: var(--text-muted);\">").append(DATE_FMT.format(item.comment().createdTime())).append("</td>\n")
                    .append("        </tr>\n");
            }
        }

        html.append("      </tbody>\n")
            .append("    </table>\n")
            .append("  </div>\n\n");

        // Customer Reviews Section (if present)
        if (analyzedReviews != null && !analyzedReviews.isEmpty()) {
            html.append("  <div class=\"card\" style=\"margin-top: 2rem;\">\n")
                .append("    <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;\">\n")
                .append("      <div class=\"card-title\" style=\"margin-bottom: 0;\">Customer Reviews & Recommendations (").append(analyzedReviews.size()).append(")</div>\n")
                .append("    </div>\n")
                .append("    <table>\n")
                .append("      <thead>\n")
                .append("        <tr>\n")
                .append("          <th style=\"width: 150px;\">Recommendation</th>\n")
                .append("          <th style=\"width: 120px;\">Sentiment</th>\n")
                .append("          <th>Customer Feedback</th>\n")
                .append("          <th style=\"width: 170px;\">Date</th>\n")
                .append("        </tr>\n")
                .append("      </thead>\n")
                .append("      <tbody>\n");

            for (AnalyzedReview ar : analyzedReviews) {
                String recBadge;
                if ("positive".equalsIgnoreCase(ar.review().recommendationType())) {
                    recBadge = "<span class=\"badge badge-success\">👍 Recommends</span>";
                } else if ("negative".equalsIgnoreCase(ar.review().recommendationType())) {
                    recBadge = "<span class=\"badge badge-danger\">👎 Doesn't Rec.</span>";
                } else if (ar.review().rating() > 0) {
                    recBadge = "<span class=\"badge badge-warning\">⭐ " + ar.review().rating() + " / 5</span>";
                } else {
                    recBadge = "<span class=\"badge badge-neutral\">Review</span>";
                }

                String sentBadge;
                switch (ar.score().level()) {
                    case CRITICAL_NEGATIVE -> sentBadge = "<span class=\"badge badge-danger\">CRITICAL (" + String.format("%.2f", ar.score().compound()) + ")</span>";
                    case WARNING_NEGATIVE -> sentBadge = "<span class=\"badge badge-warning\">WARNING (" + String.format("%.2f", ar.score().compound()) + ")</span>";
                    case POSITIVE -> sentBadge = "<span class=\"badge badge-success\">POSITIVE (" + String.format("%.2f", ar.score().compound()) + ")</span>";
                    default -> sentBadge = "<span class=\"badge badge-neutral\">NEUTRAL</span>";
                }

                String text = ar.review().reviewText() != null && !ar.review().reviewText().isBlank()
                        ? escapeHtml(ar.review().reviewText())
                        : "<span style=\"color: var(--text-muted); font-style: italic;\">[Rating only, no written text]</span>";

                String dateStr = ar.review().createdTime() != null ? DATE_FMT.format(ar.review().createdTime()) : "-";

                html.append("        <tr>\n")
                    .append("          <td>").append(recBadge).append("</td>\n")
                    .append("          <td>").append(sentBadge).append("</td>\n")
                    .append("          <td><div class=\"comment-text\">").append(text).append("</div></td>\n")
                    .append("          <td style=\"color: var(--text-muted);\">").append(dateStr).append("</td>\n")
                    .append("        </tr>\n");
            }

            html.append("      </tbody>\n")
                .append("    </table>\n")
                .append("  </div>\n\n");
        }

        // Inline Chart.js script & search filter script
        html.append("</div>\n")
            .append("<script>\n")
            .append("  const ctx = document.getElementById('sentimentChart').getContext('2d');\n")
            .append("  new Chart(ctx, {\n")
            .append("    type: 'doughnut',\n")
            .append("    data: {\n")
            .append("      labels: ['Positive', 'Neutral', 'Negative'],\n")
            .append("      datasets: [{\n")
            .append("        data: [").append(positiveCount).append(", ").append(neutralCount).append(", ").append(totalNegative).append("],\n")
            .append("        backgroundColor: ['#10b981', '#64748b', '#ef4444'],\n")
            .append("        borderWidth: 0\n")
            .append("      }]\n")
            .append("    },\n")
            .append("    options: {\n")
            .append("      responsive: true,\n")
            .append("      maintainAspectRatio: false,\n")
            .append("      plugins: { legend: { position: 'bottom', labels: { color: '#cbd5e1' } } }\n")
            .append("    }\n")
            .append("  });\n\n");

        if (totalReviews > 0) {
            html.append("  const reviewEl = document.getElementById('reviewPieChart');\n")
                .append("  if (reviewEl) {\n")
                .append("    new Chart(reviewEl.getContext('2d'), {\n")
                .append("      type: 'pie',\n")
                .append("      data: {\n");

            if (reviewOtherCount > 0) {
                html.append("        labels: ['Recommends (Yes)', \"Doesn't Recommend (No)\", 'Other'],\n")
                    .append("        datasets: [{\n")
                    .append("          data: [").append(reviewYesCount).append(", ").append(reviewNoCount).append(", ").append(reviewOtherCount).append("],\n")
                    .append("          backgroundColor: ['#10b981', '#ef4444', '#64748b'],\n")
                    .append("          borderWidth: 0\n")
                    .append("        }]\n");
            } else {
                html.append("        labels: ['Recommends (Yes)', \"Doesn't Recommend (No)\"],\n")
                    .append("        datasets: [{\n")
                    .append("          data: [").append(reviewYesCount).append(", ").append(reviewNoCount).append("],\n")
                    .append("          backgroundColor: ['#10b981', '#ef4444'],\n")
                    .append("          borderWidth: 0\n")
                    .append("        }]\n");
            }

            html.append("      },\n")
                .append("      options: {\n")
                .append("        responsive: true,\n")
                .append("        maintainAspectRatio: false,\n")
                .append("        plugins: { legend: { position: 'bottom', labels: { color: '#cbd5e1' } } }\n")
                .append("      }\n")
                .append("    });\n")
                .append("  }\n\n");
        }

        html.append("  function filterTable() {\n")
            .append("    const filter = document.getElementById('searchInput').value.toLowerCase();\n")
            .append("    const rows = document.querySelectorAll('#negativeTable tbody tr');\n")
            .append("    rows.forEach(row => {\n")
            .append("      const text = row.textContent.toLowerCase();\n")
            .append("      row.style.display = text.includes(filter) ? '' : 'none';\n")
            .append("    });\n")
            .append("  }\n")
            .append("</script>\n")
            .append("</body>\n")
            .append("</html>\n");

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, html.toString());
            System.out.println("[HtmlDashboardGenerator] Dashboard generated successfully at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write HTML report to " + outputPath, e);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
