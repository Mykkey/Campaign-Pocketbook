package dnd.map;

import java.util.List;

/** Common light sources: bright light out to the first distance and dim light out to the second, in feet. */
public final class LightPresets {
    private LightPresets() { }

    public record Preset(String name, int brightFeet, int dimFeet) {
        @Override
        public String toString() {
            return name + " (" + brightFeet + "/" + dimFeet + " ft)";
        }
    }

    public static final List<Preset> ALL = List.of(
            new Preset("Candle", 5, 10), new Preset("Lamp", 15, 30), new Preset("Torch", 20, 40),
            new Preset("Campfire", 20, 40), new Preset("Lantern", 30, 60), new Preset("Daylight spell", 60, 120));

    public static Preset find(String name) {
        for (Preset p : ALL) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }
}