package com.fbscraper.sentiment;

import com.fbscraper.enums.SentimentLevel;
import com.fbscraper.model.SentimentScore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class VaderAnalyzer {

    private static final double ALPHA = 15.0;
    private static final double C_INCR = 0.733;
    private static final double B_INCR = 0.293;
    private static final double B_DECR = -0.293;
    private static final double NEG_SCALAR = -0.74;

    private final Map<String, Double> lexicon;
    private final Map<String, Double> boosterDict;
    private final Set<String> negationWords;

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}']+|[\\S]");

    public VaderAnalyzer() {
        this(loadDefaultLexicon());
    }

    public VaderAnalyzer(Map<String, Double> lexicon) {
        this.lexicon = Objects.requireNonNull(lexicon, "lexicon cannot be null");
        this.boosterDict = initBoosterDict();
        this.negationWords = initNegationWords();
    }

    public static VaderAnalyzer createDefault() {
        return new VaderAnalyzer();
    }

    private static Map<String, Double> loadDefaultLexicon() {
        Map<String, Double> lexicon = new HashMap<>();
        try (InputStream in = VaderAnalyzer.class.getClassLoader().getResourceAsStream("vader_lexicon.txt")) {
            if (in == null) {
                throw new IllegalStateException("vader_lexicon.txt not found in resources");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\t+");
                    if (parts.length >= 2) {
                        try {
                            String token = parts[0].trim().toLowerCase(Locale.ROOT);
                            double score = Double.parseDouble(parts[1].trim());
                            lexicon.put(token, score);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load vader_lexicon.txt", e);
        }
        return lexicon;
    }

    public SentimentScore analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentScore(0.0, 0.0, 1.0, 0.0, SentimentLevel.NEUTRAL);
        }

        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return new SentimentScore(0.0, 0.0, 1.0, 0.0, SentimentLevel.NEUTRAL);
        }

        boolean textHasCaps = !text.equals(text.toLowerCase(Locale.ROOT)) && !isAllUpper(text);

        List<Double> sentiments = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lowerToken = token.toLowerCase(Locale.ROOT);

            if (lexicon.containsKey(lowerToken)) {
                double valence = lexicon.get(lowerToken);

                if (isAllUpper(token) && textHasCaps) {
                    if (valence > 0) {
                        valence += C_INCR;
                    } else {
                        valence -= C_INCR;
                    }
                }

                for (int distance = 1; distance <= 3 && (i - distance) >= 0; distance++) {
                    String prevToken = tokens.get(i - distance);
                    String prevLower = prevToken.toLowerCase(Locale.ROOT);

                    if (negationWords.contains(prevLower)) {
                        valence = valence * NEG_SCALAR;
                        break;
                    }

                    if (boosterDict.containsKey(prevLower)) {
                        double boost = boosterDict.get(prevLower);
                        if (isAllUpper(prevToken) && textHasCaps) {
                            boost += (boost > 0 ? C_INCR : -C_INCR);
                        }

                        double factor = 1.0 - (distance * 0.1);
                        if (valence > 0) {
                            valence += (boost * factor);
                        } else {
                            valence -= (boost * factor);
                        }
                    }
                }

                sentiments.add(valence);
            }
        }

        double punctEmph = countExclamations(text) * 0.292;
        if (punctEmph > 0.96) punctEmph = 0.96;

        double sumValence = sentiments.stream().mapToDouble(Double::doubleValue).sum();

        if (sumValence > 0) {
            sumValence += punctEmph;
        } else if (sumValence < 0) {
            sumValence -= punctEmph;
        }

        double compound = normalize(sumValence);

        double posSum = 0.0;
        double negSum = 0.0;
        int neuCount = 0;

        for (double s : sentiments) {
            if (s > 0.05) posSum += (s + 1.0);
            else if (s < -0.05) negSum += (Math.abs(s) + 1.0);
            else neuCount++;
        }

        neuCount += (tokens.size() - sentiments.size());

        double total = posSum + negSum + neuCount;
        double pos = total > 0 ? round3(posSum / total) : 0.0;
        double neg = total > 0 ? round3(negSum / total) : 0.0;
        double neu = total > 0 ? round3(neuCount / total) : 1.0;

        SentimentLevel level;
        if (compound <= -0.50) {
            level = SentimentLevel.CRITICAL_NEGATIVE;
        } else if (compound <= -0.05) {
            level = SentimentLevel.WARNING_NEGATIVE;
        } else if (compound >= 0.05) {
            level = SentimentLevel.POSITIVE;
        } else {
            level = SentimentLevel.NEUTRAL;
        }

        return new SentimentScore(round3(compound), pos, neu, neg, level);
    }

    private double normalize(double score) {
        return score / Math.sqrt((score * score) + ALPHA);
    }

    private static double round3(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }

    private static boolean isAllUpper(String s) {
        boolean hasLetter = false;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) return false;
            }
        }
        return hasLetter;
    }

    private static int countExclamations(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '!') count++;
        }
        return Math.min(count, 4);
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static Map<String, Double> initBoosterDict() {
        Map<String, Double> map = new HashMap<>();
        map.put("absolutely", B_INCR);
        map.put("amazingly", B_INCR);
        map.put("completely", B_INCR);
        map.put("considerably", B_INCR);
        map.put("decidedly", B_INCR);
        map.put("deeply", B_INCR);
        map.put("enormously", B_INCR);
        map.put("entirely", B_INCR);
        map.put("especially", B_INCR);
        map.put("exceptionally", B_INCR);
        map.put("extremely", B_INCR);
        map.put("fabulously", B_INCR);
        map.put("flipping", B_INCR);
        map.put("flippin", B_INCR);
        map.put("fricking", B_INCR);
        map.put("frickin", B_INCR);
        map.put("fully", B_INCR);
        map.put("greatly", B_INCR);
        map.put("heavily", B_INCR);
        map.put("highly", B_INCR);
        map.put("hugely", B_INCR);
        map.put("incredibly", B_INCR);
        map.put("intensely", B_INCR);
        map.put("majorly", B_INCR);
        map.put("more", B_INCR);
        map.put("most", B_INCR);
        map.put("particularly", B_INCR);
        map.put("purely", B_INCR);
        map.put("quite", B_INCR);
        map.put("really", B_INCR);
        map.put("remarkably", B_INCR);
        map.put("substantially", B_INCR);
        map.put("thoroughly", B_INCR);
        map.put("totally", B_INCR);
        map.put("tremendously", B_INCR);
        map.put("unbelievably", B_INCR);
        map.put("unusually", B_INCR);
        map.put("utterly", B_INCR);
        map.put("very", B_INCR);

        map.put("almost", B_DECR);
        map.put("barely", B_DECR);
        map.put("hardly", B_DECR);
        map.put("just", B_DECR);
        map.put("kinda", B_DECR);
        map.put("kind of", B_DECR);
        map.put("partly", B_DECR);
        map.put("scarcely", B_DECR);
        map.put("slightly", B_DECR);
        map.put("somewhat", B_DECR);
        return map;
    }

    private static Set<String> initNegationWords() {
        return Set.of(
                "not", "never", "none", "nobody", "nowhere", "neither", "nor", "nothing",
                "cannot", "cant", "can't", "wont", "won't", "dont", "don't", "doesnt",
                "doesn't", "isnt", "isn't", "arent", "aren't", "wasnt", "wasn't",
                "havent", "haven't", "hasnt", "hasn't", "hadnt", "hadn't", "wouldnt",
                "wouldn't", "shouldnt", "shouldn't", "couldnt", "couldn't", "without"
        );
    }
}
