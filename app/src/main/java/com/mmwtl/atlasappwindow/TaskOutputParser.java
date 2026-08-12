package com.mmwtl.atlasappwindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only task-local evidence from ActivityManager output. */
final class TaskOutputParser {
    enum Verification {
        VERIFIED,
        TASK_NOT_FOUND,
        WRONG_OWNER,
        WRONG_MODE,
        WRONG_BOUNDS,
        INDETERMINATE
    }

    private static final Pattern TASK_HEADER = Pattern.compile(
            "^\\s*(?:\\*\\s*)?Task\\{[^}]*#(\\d+)\\b|^\\s*Task id #(\\d+)\\b|"
                    + "^\\s*taskId=(\\d+):", Pattern.MULTILINE);
    private static final Pattern LEGACY_TASK_HEADER = Pattern.compile(
            "^\\s*(?:mTaskId|taskId)=(\\d+)\\b", Pattern.MULTILINE);
    private static final Pattern COMPONENT = Pattern.compile(
            "(?<![A-Za-z0-9_.$])([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)");
    private static final Pattern ROOT_COMPONENT = Pattern.compile(
            "(?:realActivity|origActivity)="
                    + "(?:ComponentInfo\\{)?([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)");
    private static final Pattern HEADER_PACKAGE = Pattern.compile("\\bA=\\d+:([A-Za-z0-9_.$]+)");
    private static final Pattern RECT_BOUNDS = Pattern.compile(
            "(?:mBounds|bounds)=Rect\\((-?\\d+),\\s*(-?\\d+)\\s*-\\s*"
                    + "(-?\\d+),\\s*(-?\\d+)\\)");
    private static final Pattern BRACKET_BOUNDS = Pattern.compile(
            "(?:mBounds|bounds)=\\[(-?\\d+),\\s*(-?\\d+)\\]"
                    + "\\[(-?\\d+),\\s*(-?\\d+)\\]");
    private static final Pattern COMPACT_BOUNDS = Pattern.compile(
            "(?:mBounds|bounds)=\\[(-?\\d+),\\s*(-?\\d+)\\s*-\\s*"
                    + "(-?\\d+),\\s*(-?\\d+)\\]");
    private static final Pattern FREEFORM = Pattern.compile(
            "(?:\\bmode=freeform\\b|\\b(?:mWindowingMode|windowingMode)=(?:freeform|5)\\b|"
                    + "\\bWINDOWING_MODE_FREEFORM\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_FREEFORM = Pattern.compile(
            "(?:\\bmode=(?:fullscreen|pinned|split-screen-primary|split-screen-secondary)\\b|"
                    + "\\b(?:mWindowingMode|windowingMode)=(?:fullscreen|pinned|"
                    + "split-screen-primary|split-screen-secondary|1|2|3|4)\\b)",
            Pattern.CASE_INSENSITIVE);

    private TaskOutputParser() {}

    /**
     * Returns an id only when one and only one task contains the exact requested component.
     * Package-only evidence is deliberately insufficient for task mutation/removal.
     */
    static int taskIdForComponent(String output, String component) {
        Set<Integer> ids = taskIdsForComponent(output, component);
        return ids.size() == 1 ? ids.iterator().next() : -1;
    }

    static Set<Integer> taskIdsForComponent(String output, String component) {
        String normalized = normalizeComponent(ShellCommandBuilder.requireComponent(component));
        Set<Integer> result = new LinkedHashSet<>();
        for (TaskBlock block : taskBlocks(output)) {
            if (block.hasExactComponent(normalized)) result.add(block.id);
        }
        return Collections.unmodifiableSet(result);
    }

    static Set<Integer> taskIdsForPackage(String output, String packageName) {
        String validated = CommandValidation.requirePackageName(packageName);
        Set<Integer> result = new LinkedHashSet<>();
        for (TaskBlock block : taskBlocks(output)) {
            if (block.hasPackage(validated)) result.add(block.id);
        }
        return Collections.unmodifiableSet(result);
    }

    static boolean containsTask(String output, int taskId) {
        CommandValidation.requirePositiveTaskId(taskId);
        for (TaskBlock block : taskBlocks(output)) {
            if (block.id == taskId) return true;
        }
        return false;
    }

    static boolean taskHasExactComponent(String output, int taskId, String component) {
        String normalized = normalizeComponent(ShellCommandBuilder.requireComponent(component));
        for (TaskBlock block : taskBlocks(output)) {
            if (block.id == taskId) return block.hasExactComponent(normalized);
        }
        return false;
    }

    static Verification verifyTask(
            String output, int taskId, String component, WindowBounds expectedBounds) {
        CommandValidation.requirePositiveTaskId(taskId);
        if (expectedBounds == null) throw new NullPointerException("expectedBounds");
        String normalized = normalizeComponent(ShellCommandBuilder.requireComponent(component));
        for (TaskBlock block : taskBlocks(output)) {
            if (block.id != taskId) continue;
            if (!block.hasExactComponent(normalized)) return Verification.WRONG_OWNER;
            if (NON_FREEFORM.matcher(block.text).find()) return Verification.WRONG_MODE;
            boolean freeform = FREEFORM.matcher(block.text).find();
            WindowBounds actual = parseBounds(block.text);
            if (actual != null && !actual.equals(expectedBounds)) return Verification.WRONG_BOUNDS;
            if (!freeform || actual == null) return Verification.INDETERMINATE;
            return Verification.VERIFIED;
        }
        return Verification.TASK_NOT_FOUND;
    }

    /** Verifies geometry without treating package-only evidence as task ownership. */
    static Verification verifyTaskGeometry(
            String output, int taskId, WindowBounds expectedBounds) {
        CommandValidation.requirePositiveTaskId(taskId);
        if (expectedBounds == null) throw new NullPointerException("expectedBounds");
        for (TaskBlock block : taskBlocks(output)) {
            if (block.id != taskId) continue;
            if (NON_FREEFORM.matcher(block.text).find()) return Verification.WRONG_MODE;
            boolean freeform = FREEFORM.matcher(block.text).find();
            WindowBounds actual = parseBounds(block.text);
            if (actual != null && !actual.equals(expectedBounds)) return Verification.WRONG_BOUNDS;
            if (!freeform || actual == null) return Verification.INDETERMINATE;
            return Verification.VERIFIED;
        }
        return Verification.TASK_NOT_FOUND;
    }

    static boolean reportsFreeform(String output, int taskId) {
        CommandValidation.requirePositiveTaskId(taskId);
        for (TaskBlock block : taskBlocks(output)) {
            if (block.id == taskId) return FREEFORM.matcher(block.text).find()
                    && !NON_FREEFORM.matcher(block.text).find();
        }
        return false;
    }

    private static List<TaskBlock> taskBlocks(String output) {
        String text = output == null ? "" : output;
        Matcher headers = TASK_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        while (headers.find()) {
            starts.add(headers.start());
            String raw = headers.group(1) != null ? headers.group(1)
                    : headers.group(2) != null ? headers.group(2) : headers.group(3);
            try {
                ids.add(Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                starts.remove(starts.size() - 1);
            }
        }
        if (starts.isEmpty()) {
            headers = LEGACY_TASK_HEADER.matcher(text);
            while (headers.find()) {
                try {
                    starts.add(headers.start());
                    ids.add(Integer.parseInt(headers.group(1)));
                } catch (NumberFormatException ignored) {
                    if (starts.size() > ids.size()) starts.remove(starts.size() - 1);
                }
            }
        }
        List<TaskBlock> blocks = new ArrayList<>(starts.size());
        for (int index = 0; index < starts.size(); index++) {
            int end = index + 1 < starts.size() ? starts.get(index + 1) : text.length();
            blocks.add(new TaskBlock(ids.get(index), text.substring(starts.get(index), end)));
        }
        return blocks;
    }

    private static WindowBounds parseBounds(String text) {
        Matcher matcher = RECT_BOUNDS.matcher(text);
        if (!matcher.find()) {
            matcher = BRACKET_BOUNDS.matcher(text);
            if (!matcher.find()) {
                matcher = COMPACT_BOUNDS.matcher(text);
                if (!matcher.find()) return null;
            }
        }
        try {
            return new WindowBounds(
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeComponent(String component) {
        int separator = component.indexOf('/');
        String packageName = component.substring(0, separator);
        String className = component.substring(separator + 1);
        return packageName + "/" + (className.startsWith(".") ? packageName + className : className);
    }

    private static final class TaskBlock {
        final int id;
        final String text;

        TaskBlock(int id, String text) {
            this.id = id;
            this.text = text;
        }

        boolean hasExactComponent(String normalizedTarget) {
            Matcher root = ROOT_COMPONENT.matcher(text);
            while (root.find()) {
                if (normalizeComponent(root.group(1)).equals(normalizedTarget)) return true;
            }
            String targetPackage = normalizedTarget.substring(0, normalizedTarget.indexOf('/'));
            if (!hasHeaderPackage(targetPackage)) return false;
            Matcher any = COMPONENT.matcher(text);
            while (any.find()) {
                if (normalizeComponent(any.group(1)).equals(normalizedTarget)) return true;
            }
            return false;
        }

        boolean hasPackage(String packageName) {
            if (hasHeaderPackage(packageName)) return true;
            Matcher component = COMPONENT.matcher(text);
            while (component.find()) {
                if (component.group(1).startsWith(packageName + "/")) return true;
            }
            return false;
        }

        private boolean hasHeaderPackage(String packageName) {
            Matcher header = HEADER_PACKAGE.matcher(text);
            while (header.find()) {
                if (header.group(1).equals(packageName)) return true;
            }
            return false;
        }
    }
}
