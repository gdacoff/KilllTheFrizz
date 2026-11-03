package io.github.gdacoff.killthefrizz.config;

/**
 * Optimization presets. When the active preset is not {@link #CUSTOM}, the effective
 * values of every feature are taken from the preset table instead of the config file.
 * The preset never rewrites the config file; it only affects "effective" values.
 */
public enum Preset {
    OFF,
    SAFE,
    BALANCED,
    AGGRESSIVE,
    CUSTOM
}
