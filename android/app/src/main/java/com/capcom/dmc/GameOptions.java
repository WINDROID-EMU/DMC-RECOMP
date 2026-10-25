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

    static final String RENDER_TARGET_PATH = "render_target_path";
    static final String VULKAN_ASYNC_SKIP = "vulkan_async_skip";
    static final String VULKAN_THREADS = "vulkan_threads";
    static final String RESOLUTION_SCALE = "resolution_scale";
    static final String FPS = "fps";
    static final String VSYNC = "vsync";
    static final String STORE_SHADERS = "store_shaders";
    static final String POST_EFFECT = "swap_post_effect";

    static final Option[] ALL = {
            new Option(RENDER_TARGET_PATH, "Modo Render Target (Vulkan)", "render_target_path_vulkan", "fbo",
                    new String[] {"fbo", "fsi"},
                    new String[] {"FBO · Alto Desempenho (Hardware GMEM)", "FSI · Lento (Software Interlock)"}),
            new Option(VULKAN_ASYNC_SKIP, "Anti-Stutter de Shaders", "vulkan_async_skip_incomplete_frames", "true",
                    new String[] {"true", "false"},
                    new String[] {"Ativado · Fluido (Pula quadros incompletos)", "Desativado · Síncrono (Congela a tela)"}),
            new Option(VULKAN_THREADS, "Threads de Shaders Vulkan", "vulkan_pipeline_creation_threads", "6",
                    new String[] {"2", "4", "6", "8"},
                    new String[] {"2 Threads (Econômico)", "4 Threads (Equilibrado)",
                            "6 Threads (Rápido · Adreno 730)", "8 Threads (Máximo)"}),
            new Option(RESOLUTION_SCALE, "Escala de Renderização", "resolution_scale", "1",
                    new String[] {"1", "2"},
                    new String[] {"1x (720p Nativo X360 · Máximo FPS)", "2x (1440p 2K · Alta Nitidez)"}),
            new Option(FPS, "Límite de FPS", "video_mode_refresh_rate", "60",
                    new String[] {"30", "60", "90", "120"},
                    new String[] {"30 FPS · Ahorra batería", "60 FPS · Fluidez padrão", "90 FPS · Fluidez alta",
                            "120 FPS · Tela rápida"}),
            new Option(VSYNC, "Sincronização Vertical (V-Sync)", "vsync", "true",
                    new String[] {"true", "false"},
                    new String[] {"Ativado (Sem cortes de tela)", "Desativado (Menor latência / FPS livre)"}),
            new Option(STORE_SHADERS, "Cache de Shaders no Disco", "store_shaders", "true",
                    new String[] {"true", "false"},
                    new String[] {"Ativado (Salva shaders em disco)", "Desativado"}),
            new Option(POST_EFFECT, "Pós-Processamento (Anti-Aliasing)", "swap_post_effect", "none",
                    new String[] {"none", "fxaa", "fxaa_extreme"},
                    new String[] {"Nenhum (Máximo desempenho)", "FXAA (Suavização)", "FXAA Extremo"}),
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
