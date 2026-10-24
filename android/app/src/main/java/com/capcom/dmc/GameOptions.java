package com.capcom.dmc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The graphics and runtime options of the launcher.
 */
final class GameOptions {
    static final class Option {
        final String key;
        final String title;
        final String cvar;
        final String[] values;
        final String[] labels;
        final String defaultValue;

        Option(String key, String title, String cvar, String defaultValue, String[] values, String[] labels) {
            this.key = key;
            this.title = title;
            this.cvar = cvar;
            this.defaultValue = defaultValue;
            this.values = values;
            this.labels = labels;
        }

        String label(String value) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(value)) {
                    return labels[i];
                }
            }
            return value;
        }
    }

    static final String RESOLUTION = "resolution";
    static final String FPS = "fps";

    static final Option[] ALL = {
            new Option(RESOLUTION, "Resolución interna", "dmc_resolucion_interna", "1280x720",
                    new String[] {"640x360", "1024x576", "1280x720", "1920x1080"},
                    new String[] {"640x360 · máximo rendimiento", "1024x576 · rendimiento", "1280x720 · equilibrado",
                            "1920x1080 · máxima calidad"}),
            new Option(FPS, "Límite de FPS", "dmc_limite_fps", "60",
                    new String[] {"30", "60", "90", "120"},
                    new String[] {"30 FPS · ahorra batería", "60 FPS", "90 FPS · fluidez alta",
                            "120 FPS · pantalla rápida"}),
            new Option("volume", "Volumen del juego", "audio_ganancia_pct", "100",
                    new String[] {"100", "125", "150", "200"},
                    new String[] {"Normal", "Alto", "Muy alto", "Máximo"}),
            new Option("audio_buffer", "Búfer de audio", "audio_sdl_bomba_cola", "12",
                    new String[] {"12", "20"},
                    new String[] {"Normal · 64 ms", "Grande · 107 ms, si hay cortes"}),
    };

    private static final String PREFS = "dmc_game";

    private GameOptions() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Option find(String key) {
        for (Option o : ALL) {
            if (o.key.equals(key)) {
                return o;
            }
        }
        throw new IllegalArgumentException(key);
    }

    static String get(Context context, String key) {
        Option o = find(key);
        String value = prefs(context).getString(key, o.defaultValue);
        for (String allowed : o.values) {
            if (allowed.equals(value)) {
                return value;
            }
        }
        return o.defaultValue;
    }

    static void set(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }

    static List<String> arguments(Context context) {
        List<String> args = new ArrayList<>();
        for (Option o : ALL) {
            args.add("--" + o.cvar + "=" + get(context, o.key));
        }
        GpuDrivers.arguments(context, args);
        return args;
    }
}
