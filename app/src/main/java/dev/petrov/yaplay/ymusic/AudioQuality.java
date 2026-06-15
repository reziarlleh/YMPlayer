package dev.petrov.yaplay.ymusic;

import java.util.Locale;

import dev.petrov.yaplay.player.YmpSettings;

public final class AudioQuality {
    public final String value;

    private AudioQuality(String value) {
        this.value = YmpSettings.normalizeQuality(value);
    }

    public static AudioQuality from(String value) {
        return new AudioQuality(value);
    }

    public int targetBitrateKbps() {
        switch (value) {
            case YmpSettings.QUALITY_ECONOMY:
                return 128;
            case YmpSettings.QUALITY_STANDARD:
                return 192;
            case YmpSettings.QUALITY_HIGH:
                return 320;
            case YmpSettings.QUALITY_MAX:
            case YmpSettings.QUALITY_AUTO:
            default:
                return Integer.MAX_VALUE;
        }
    }

    public boolean preferHighest() {
        return YmpSettings.QUALITY_AUTO.equals(value) || YmpSettings.QUALITY_MAX.equals(value);
    }

    @Override
    public String toString() {
        return value.toLowerCase(Locale.US);
    }
}
