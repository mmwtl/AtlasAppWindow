package com.mmwtl.atlasappwindow;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class Preset {
    static final int MAX_COUNT = 5;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    final String id;
    final String component;
    final String label;
    final int slot;

    Preset(String id, String component, String label) {
        this(id, component, label, 0);
    }

    Preset(String id, String component, String label, int slot) {
        if (!ID.matcher(id == null ? "" : id).matches()) {
            throw new IllegalArgumentException("Invalid preset id");
        }
        if (slot < 0 || slot > MAX_COUNT) {
            throw new IllegalArgumentException("Invalid preset slot");
        }
        ShellCommandBuilder.requireComponent(component);
        this.id = id;
        this.component = component;
        this.label = label == null || label.isBlank() ? component : label.trim();
        this.slot = slot;
    }

    Preset inSlot(int assignedSlot) {
        return new Preset(id, component, label, assignedSlot);
    }

    static String idFor(String label, String component) {
        String source = label == null || label.isBlank() ? component : label;
        String ascii = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (ascii.isEmpty()) ascii = "app";
        if (ascii.length() > 48) ascii = ascii.substring(0, 48).replaceAll("-+$", "");
        String hash = Integer.toUnsignedString(component.hashCode(), 36);
        return ascii + "-" + hash;
    }
}
