package com.fbscraper.report;

import com.fbscraper.model.AnalyzedComment;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.SentimentLevel;
import com.fbscraper.model.StarRatingBreakdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates an interactive, self-contained HTML5/CSS3 dashboard highlighting
 * customer sentiment, KPI statistics, Page ratings, 5-star distributions, and reviews.
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
     * Overloaded report generation method accepting reviews and rating summary.
     */
    public void generateReport(
            List<FacebookPost> posts,
            List<AnalyzedComment> analyzedComments,
            PageRatingSummary ratingSummary,
            List<AnalyzedReview> analyzedReviews,
            Path outputPath
    ) {
        StarRatingBreakdown starRating = StarRatingBreakdown.compute(analyzedComments, analyzedReviews);
        generateReport(posts, analyzedComments, ratingSummary, analyzedReviews, starRating, outputPath);
    }

    /**
     * Generates a comprehensive HTML dashboard report including post comments, page ratings, 5-star distribution, and reviews.
     *
     * @param posts            the list of scraped Facebook posts
     * @param analyzedComments the list of sentiment-analyzed comments
     * @param ratingSummary    overall page rating statistics from Meta (or EMPTY)
     * @param analyzedReviews  the list of customer reviews and recommendations
     * @param starRating       the calculated 5-star rating breakdown across all feedback
     * @param outputPath       the destination file path (e.g. output/dashboard.html)
     */
    public void generateReport(
            List<FacebookPost> posts,
            List<AnalyzedComment> analyzedComments,
            PageRatingSummary ratingSummary,
            List<AnalyzedReview> analyzedReviews,
            StarRatingBreakdown starRating,
            Path outputPath
    ) {
        if (starRating == null) {
            starRating = StarRatingBreakdown.compute(analyzedComments, analyzedReviews);
        }

        long totalPosts = posts != null ? posts.size() : 0;
        long totalComments = analyzedComments != null ? analyzedComments.size() : 0;
        long totalReviews = analyzedReviews != null ? analyzedReviews.size() : 0;

        long positiveCount = analyzedComments != null ? analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.POSITIVE).count() : 0;
        long neutralCount = analyzedComments != null ? analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.NEUTRAL).count() : 0;
        long warningCount = analyzedComments != null ? analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.WARNING_NEGATIVE).count() : 0;
        long criticalCount = analyzedComments != null ? analyzedComments.stream().filter(c -> c.score().level() == SentimentLevel.CRITICAL_NEGATIVE).count() : 0;
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
            .append("    .grid-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.25rem; margin-bottom: 2rem; }\n")
            .append("    .card { background-color: var(--card-bg); border: 1px solid var(--card-border); border-radius: 0.75rem; padding: 1.5rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.2); }\n")
            .append("    .card-title { font-size: 0.875rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem; }\n")
            .append("    .card-value { font-size: 2rem; font-weight: 700; }\n")
            .append("    .grid-charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; margin-bottom: 2rem; }\n")
            .append("    .chart-container { height: 260px; position: relative; display: flex; justify-content: center; align-items: center; }\n")
            .append("    .badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }\n")
            .append("    .badge-success { background-color: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.4); }\n")
            .append("    .badge-warning { background-color: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.4); }\n")
            .append("    .badge-danger { background-color: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.4); }\n")
            .append("    .badge-neutral { background-color: rgba(148, 163, 184, 0.2); color: #cbd5e1; border: 1px solid rgba(148, 163, 184, 0.4); }\n")
            .append("    .star-bar-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.65rem; font-size: 0.85rem; }\n")
            .append("    .star-label { width: 45px; font-weight: 600; color: #fbbf24; text-align: right; }\n")
            .append("    .star-progress { flex: 1; height: 10px; background-color: #334155; border-radius: 9999px; overflow: hidden; }\n")
            .append("    .star-progress-fill { height: 100%; border-radius: 9999px; transition: width 0.3s ease; }\n")
            .append("    .star-count { width: 85px; text-align: right; color: var(--text-muted); font-size: 0.8rem; font-weight: 500; }\n")
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
            .append("    </div>\n");

        if (totalReviews > 0) {
            html.append("    <div class=\"card\">\n")
                .append("      <div class=\"card-title\">Customer Reviews</div>\n")
                .append("      <div class=\"card-value\">").append(totalReviews).append("</div>\n")
                .append("    </div>\n");
        }

        // Calculated 5-Star Rating Card
        html.append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Overall 5-Star Rating</div>\n")
            .append("      <div class=\"card-value\" style=\"color: #fbbf24;\">⭐ ")
            .append(String.format("%.1f", starRating.averageRating()))
            .append(" <span style=\"font-size: 1rem; color: var(--text-muted); font-weight: normal;\">/ 5.0</span></div>\n")
            .append("      <div class=\"subtitle\" style=\"color: #cbd5e1;\">")
            .append(starRating.formattedStars())
            .append(" · ").append(starRating.totalPositive()).append(" pos, ")
            .append(starRating.totalNegative()).append(" neg</div>\n")
            .append("    </div>\n");

        if (ratingSummary != null && ratingSummary.hasRatings()) {
            html.append("    <div class=\"card\">\n")
                .append("      <div class=\"card-title\">Meta Official Rating</div>\n")
                .append("      <div class=\"card-value\" style=\"color: #fbbf24;\">⭐ ")
                .append(String.format("%.1f", ratingSummary.overallStarRating()))
                .append(" <span style=\"font-size: 1rem; color: var(--text-muted); font-weight: normal;\">/ 5.0</span></div>\n")
                .append("      <div class=\"subtitle\">").append(ratingSummary.ratingCount()).append(" total Meta ratings</div>\n")
                .append("    </div>\n");
        }

        html.append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Positive Feedback Rate</div>\n")
            .append("      <div class=\"card-value\" style=\"color: var(--success);\">")
            .append(String.format("%.1f%%", positivePercent)).append("</div>\n")
            .append("      <div class=\"subtitle\">").append(positiveCount).append(" positive comments</div>\n")
            .append("    </div>\n");

        html.append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Negative Feedback Rate</div>\n")
            .append("      <div class=\"card-value\" style=\"color: ").append(negativePercent > 20 ? "var(--danger)" : "var(--text-main)").append(";\">")
            .append(String.format("%.1f%%", negativePercent)).append("</div>\n")
            .append("      <div class=\"subtitle\">").append(totalNegative).append(" flagged comments</div>\n")
            .append("    </div>\n")
            .append("  </div>\n\n");

        // Charts & 5-Star Breakdown Section
        html.append("  <div class=\"grid-charts\">\n")
            // Sentiment Breakdown Doughnut
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Sentiment Breakdown</div>\n")
            .append("      <div class=\"chart-container\">\n")
            .append("        <canvas id=\"sentimentChart\"></canvas>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            // 5-Star Distribution Breakdown Card
            .append("    <div class=\"card\">\n")
            .append("      <div style=\"display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 1.25rem;\">\n")
            .append("        <div class=\"card-title\" style=\"margin-bottom: 0;\">5-Star Rating Breakdown</div>\n")
            .append("        <div style=\"color: #fbbf24; font-weight: 700; font-size: 1.15rem;\">⭐ ")
            .append(String.format("%.1f", starRating.averageRating()))
            .append(" <span style=\"color: #cbd5e1; font-size: 0.9rem; letter-spacing: 2px;\">")
            .append(starRating.formattedStars()).append("</span></div>\n")
            .append("      </div>\n")
            // 5 Star bar
            .append("      <div class=\"star-bar-row\">\n")
            .append("        <span class=\"star-label\">5 ★</span>\n")
            .append("        <div class=\"star-progress\"><div class=\"star-progress-fill\" style=\"width: ")
            .append(String.format("%.1f", starRating.fiveStarPercent())).append("%; background-color: #10b981;\"></div></div>\n")
            .append("        <span class=\"star-count\">").append(String.format("%.1f%% (%d)", starRating.fiveStarPercent(), starRating.fiveStarCount())).append("</span>\n")
            .append("      </div>\n")
            // 4 Star bar
            .append("      <div class=\"star-bar-row\">\n")
            .append("        <span class=\"star-label\">4 ★</span>\n")
            .append("        <div class=\"star-progress\"><div class=\"star-progress-fill\" style=\"width: ")
            .append(String.format("%.1f", starRating.fourStarPercent())).append("%; background-color: #3b82f6;\"></div></div>\n")
            .append("        <span class=\"star-count\">").append(String.format("%.1f%% (%d)", starRating.fourStarPercent(), starRating.fourStarCount())).append("</span>\n")
            .append("      </div>\n")
            // 3 Star bar
            .append("      <div class=\"star-bar-row\">\n")
            .append("        <span class=\"star-label\">3 ★</span>\n")
            .append("        <div class=\"star-progress\"><div class=\"star-progress-fill\" style=\"width: ")
            .append(String.format("%.1f", starRating.threeStarPercent())).append("%; background-color: #94a3b8;\"></div></div>\n")
            .append("        <span class=\"star-count\">").append(String.format("%.1f%% (%d)", starRating.threeStarPercent(), starRating.threeStarCount())).append("</span>\n")
            .append("      </div>\n")
            // 2 Star bar
            .append("      <div class=\"star-bar-row\">\n")
            .append("        <span class=\"star-label\">2 ★</span>\n")
            .append("        <div class=\"star-progress\"><div class=\"star-progress-fill\" style=\"width: ")
            .append(String.format("%.1f", starRating.twoStarPercent())).append("%; background-color: #f59e0b;\"></div></div>\n")
            .append("        <span class=\"star-count\">").append(String.format("%.1f%% (%d)", starRating.twoStarPercent(), starRating.twoStarCount())).append("</span>\n")
            .append("      </div>\n")
            // 1 Star bar
            .append("      <div class=\"star-bar-row\">\n")
            .append("        <span class=\"star-label\">1 ★</span>\n")
            .append("        <div class=\"star-progress\"><div class=\"star-progress-fill\" style=\"width: ")
            .append(String.format("%.1f", starRating.oneStarPercent())).append("%; background-color: #ef4444;\"></div></div>\n")
            .append("        <span class=\"star-count\">").append(String.format("%.1f%% (%d)", starRating.oneStarPercent(), starRating.oneStarCount())).append("</span>\n")
            .append("      </div>\n")
            .append("      <div style=\"margin-top: 0.9rem; padding-top: 0.75rem; border-top: 1px solid var(--card-border); font-size: 0.8rem; color: var(--text-muted); display: flex; justify-content: space-between;\">\n")
            .append("        <span>Evaluated: <strong>").append(starRating.totalCount()).append("</strong> items</span>\n")
            .append("        <span>Pos: <strong style=\"color: var(--success);\">").append(starRating.totalPositive()).append("</strong> | Neg: <strong style=\"color: var(--danger);\">").append(starRating.totalNegative()).append("</strong></span>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            // Action items card
            .append("    <div class=\"card\">\n")
            .append("      <div class=\"card-title\">Analysis Summary & Action Items</div>\n")
            .append("      <ul style=\"color: var(--text-muted); padding-left: 1.25rem; margin-top: 1rem; line-height: 1.8;\">\n")
            .append("        <li><strong>5-Star Feedback:</strong> ").append(starRating.fiveStarCount()).append(" strong positive endorsements.</li>\n")
            .append("        <li><strong>Critical 1-Star issues:</strong> ").append(starRating.oneStarCount()).append(" severe complaints requiring immediate attention.</li>\n")
            .append("        <li><strong>4-Star & 2-Star:</strong> ").append(starRating.fourStarCount()).append(" satisfied and ").append(starRating.twoStarCount()).append(" mildly dissatisfied users.</li>\n")
            .append("        <li><strong>Neutral 3-Star:</strong> ").append(starRating.threeStarCount()).append(" standard questions or comments.</li>\n")
            .append("      </ul>\n")
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
            .append("          <th style=\"width: 100px;\">Rating</th>\n")
            .append("          <th style=\"width: 110px;\">Severity</th>\n")
            .append("          <th style=\"width: 80px;\">Score</th>\n")
            .append("          <th>Comment & Related Post</th>\n")
            .append("          <th style=\"width: 170px;\">Date</th>\n")
            .append("        </tr>\n")
            .append("      </thead>\n")
            .append("      <tbody>\n");

        List<AnalyzedComment> negativeComments = analyzedComments != null ? analyzedComments.stream()
                .filter(c -> c.score().isNegative())
                .sorted((a, b) -> Double.compare(a.score().compound(), b.score().compound())) // Most negative first
                .toList() : List.of();

        if (negativeComments.isEmpty()) {
            html.append("        <tr><td colspan=\"5\" style=\"text-align: center; color: var(--text-muted); padding: 2rem;\">🎉 No negative comments detected! Great job!</td></tr>\n");
        } else {
            for (AnalyzedComment item : negativeComments) {
                String badge = item.score().level() == SentimentLevel.CRITICAL_NEGATIVE ?
                        "<span class=\"badge badge-danger\">CRITICAL</span>" :
                        "<span class=\"badge badge-warning\">WARNING</span>";

                String starBadge = "<span class=\"badge\" style=\"background-color: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3);\">⭐ " + item.score().starRating() + "/5</span>";

                html.append("        <tr>\n")
                    .append("          <td>").append(starBadge).append("</td>\n")
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
                .append("          <th style=\"width: 100px;\">Rating</th>\n")
                .append("          <th style=\"width: 150px;\">Recommendation</th>\n")
                .append("          <th style=\"width: 120px;\">Sentiment</th>\n")
                .append("          <th>Customer Feedback</th>\n")
                .append("          <th style=\"width: 170px;\">Date</th>\n")
                .append("        </tr>\n")
                .append("      </thead>\n")
                .append("      <tbody>\n");

            for (AnalyzedReview ar : analyzedReviews) {
                int reviewStars = StarRatingBreakdown.mapReviewToStars(ar);
                String starBadge = "<span class=\"badge\" style=\"background-color: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3);\">⭐ " + reviewStars + "/5</span>";

                String recBadge;
                if ("positive".equalsIgnoreCase(ar.review().recommendationType())) {
                    recBadge = "<span class=\"badge badge-success\">👍 Recommends</span>";
                } else if ("negative".equalsIgnoreCase(ar.review().recommendationType())) {
                    recBadge = "<span class=\"badge badge-danger\">👎 Doesn't Rec.</span>";
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
                    .append("          <td>").append(starBadge).append("</td>\n")
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
            .append("  });\n\n")
            .append("  function filterTable() {\n")
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
