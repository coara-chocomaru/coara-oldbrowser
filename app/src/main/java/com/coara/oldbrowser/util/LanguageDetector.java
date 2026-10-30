package com.coara.oldbrowser.util;

import android.content.Context;
import android.icu.util.ULocale;
import android.os.Build;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LanguageDetector {
    public interface Callback {
        void onResult(String tag);
    }

    private static final float CONFIDENCE = 0.65f;
    private static final Map<String, String[]> STOPWORDS = new HashMap<>();
    private static ExecutorService executor;

    static {
        STOPWORDS.put("en", "the and of to in is that for with on as are this it was be have from or an at by not".split(" "));
        STOPWORDS.put("fr", "le la les des et est une que pour dans pas sur qui avec ce au du en ne se vous nous".split(" "));
        STOPWORDS.put("de", "der die das und ist nicht ein eine mit für auf den zu von im dem sich auch es wir sie".split(" "));
        STOPWORDS.put("es", "el los las una que por con para del es en se no un su al lo como más pero".split(" "));
        STOPWORDS.put("it", "il di che per con una sono della non gli le un del dei nel più come anche questo".split(" "));
        STOPWORDS.put("pt", "o os um uma que para com não por em do da dos das no na você mais como seu foi".split(" "));
        STOPWORDS.put("nl", "de het een en van is dat niet op te voor met zijn aan er maar ook om als".split(" "));
    }

    private LanguageDetector() {
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "coara-langid");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    public static synchronized void release() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static void identify(final Context context, final String text, final Callback callback) {
        final Context app = context.getApplicationContext();
        try {
            executor().execute(() -> {
                String tag;
                try {
                    tag = identifyBlocking(app, text);
                } catch (Throwable t) {
                    tag = "und";
                }
                final String result = tag;
                UiThread.mainHandler().post(() -> callback.onResult(result));
            });
        } catch (Throwable t) {
            callback.onResult("und");
        }
    }

    public static String identifyBlocking(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            return "und";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                TextClassificationManager manager = (TextClassificationManager) context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE);
                if (manager != null) {
                    TextClassifier classifier = manager.getTextClassifier();
                    TextLanguage result = classifier.detectLanguage(new TextLanguage.Request.Builder(text).build());
                    if (result.getLocaleHypothesisCount() > 0) {
                        ULocale locale = result.getLocale(0);
                        if (result.getConfidenceScore(locale) >= CONFIDENCE) {
                            String language = locale.getLanguage();
                            if (language != null && !language.isEmpty()) {
                                return language;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return heuristic(text);
    }

    private static String heuristic(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, Integer> score = new HashMap<>();
        int start = -1;
        int words = 0;
        for (int i = 0; i <= lower.length(); i++) {
            boolean letter = i < lower.length() && Character.isLetter(lower.charAt(i));
            if (letter) {
                if (start < 0) {
                    start = i;
                }
                continue;
            }
            if (start >= 0) {
                String word = lower.substring(start, i);
                start = -1;
                words++;
                if (word.length() > 4) {
                    continue;
                }
                for (Map.Entry<String, String[]> entry : STOPWORDS.entrySet()) {
                    for (String stop : entry.getValue()) {
                        if (stop.equals(word)) {
                            Integer old = score.get(entry.getKey());
                            score.put(entry.getKey(), old == null ? 1 : old + 1);
                            break;
                        }
                    }
                }
            }
        }
        String best = null;
        int bestScore = 0;
        int second = 0;
        for (Map.Entry<String, Integer> entry : score.entrySet()) {
            int value = entry.getValue();
            if (value > bestScore) {
                second = bestScore;
                bestScore = value;
                best = entry.getKey();
            } else if (value > second) {
                second = value;
            }
        }
        if (best == null || words < 6 || bestScore < 3 || bestScore * 10 < second * 14) {
            return "und";
        }
        return best;
    }
}
