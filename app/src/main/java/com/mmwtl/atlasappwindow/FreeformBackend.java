package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class FreeformBackend implements WindowBackend {
    private final Context context;
    private final Prefs prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "atlas-freeform-backend");
        thread.setDaemon(true);
        return thread;
    });
    /** Invalidates stale verification results when show/hide/resize requests race. */
    private final AtomicLong operationGeneration = new AtomicLong();
    private volatile BackendStatus status = new BackendStatus(
            BackendStatus.State.IDLE, "Проверка ещё не выполнялась", null, Prefs.NO_TASK);
    private volatile boolean closed;

    FreeformBackend(Context context, Prefs prefs, Listener listener) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.listener = listener;
    }

    @Override public void probe() {
        long generation = operationGeneration.get();
        submit(() -> {
            if (!mayPublishProbe(generation)) return;
            boolean declared = context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
            boolean setting = false;
            try {
                setting = Settings.Global.getInt(
                        context.getContentResolver(), "enable_freeform_support", 0) == 1;
            } catch (RuntimeException ignored) {}
            String direct = declared || setting
                    ? "Прямой freeform включён"
                    : "Freeform не объявлен; пробный запуск всё равно доступен";
            publishProbe(generation, BackendStatus.State.CONNECTING,
                    direct + " • проверка локального ADB…");
            try (AdbShellClient shell = connected()) {
                AdbShellClient.Result version = shell.execute("getprop ro.build.version.release");
                AdbShellClient.Result help = shell.execute("am task help 2>&1 || am help 2>&1");
                boolean resize = help.output.contains("resize");
                publishProbe(generation, BackendStatus.State.READY,
                        direct + " • ADB готов • Android " + version.output.trim()
                                + (resize ? " • resize доступен" : " • resize не подтверждён"));
            } catch (Exception error) {
                AppLog.warn("Optional local ADB probe failed", error);
                publishProbe(generation, BackendStatus.State.READY,
                        direct + " • ADB недоступен (проверка/resize/close ограничены)");
            }
        });
    }

    @Override public void show(Preset preset, WindowBounds bounds) {
        if (closed) return;
        if (preset == null) {
            long generation = operationGeneration.incrementAndGet();
            fail(generation, "Пресет не найден", null, null);
            return;
        }
        if (bounds == null) throw new NullPointerException("bounds");

        OwnedTask previous = ownedTaskFromCurrentStatus();
        long generation = operationGeneration.incrementAndGet();
        publish(generation, BackendStatus.State.LAUNCHING,
                "Запуск " + preset.label + "…", preset, Prefs.NO_TASK);

        // The Activity launch is intentionally not queued behind a potentially slow ADB probe.
        boolean directStarted = false;
        try {
            launchDirect(generation, preset, bounds);
            if (!isCurrent(generation)) return;
            directStarted = true;
            if (isCurrent(generation)) {
                prefs.putString(Prefs.KEY_ACTIVE_PRESET, preset.id);
                prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
            }
        } catch (Exception error) {
            AppLog.warn("Direct freeform launch failed; trying shell fallback", error);
        }

        final boolean launchedDirectly = directStarted;
        submit(() -> finishShow(generation, preset, bounds, launchedDirectly, previous));
    }

    private void finishShow(
            long generation,
            Preset preset,
            WindowBounds bounds,
            boolean directStarted,
            OwnedTask previous) {
        if (!isCurrent(generation)) return;
        try (AdbShellClient shell = connected()) {
            if (!isCurrent(generation)) return;
            if (!directStarted) {
                AdbShellClient.Result launch = shell.execute(
                        ShellCommandBuilder.buildLaunchCommand(preset.component));
                if (!launch.ok()) {
                    fail(generation, "Shell-запуск отклонён: " + launch.output, null, preset);
                    return;
                }
                if (!isCurrent(generation)) {
                    restoreHomeAfterStaleLaunch();
                    return;
                }
                prefs.putString(Prefs.KEY_ACTIVE_PRESET, preset.id);
            }

            String dump = waitForTaskEvidence(shell, preset.component);
            if (!isCurrent(generation)) return;
            assessAndAdopt(generation, shell, dump, preset, bounds, previous);
        } catch (Exception error) {
            if (!isCurrent(generation)) return;
            if (directStarted) {
                AppLog.warn("Task verification unavailable", error);
                // Direct launch remains useful without local ADB, but the lack of proof is explicit.
                publish(generation, BackendStatus.State.ACTIVE,
                        preset.label + " • запуск запрошен • результат не проверен (ADB недоступен)",
                        preset, Prefs.NO_TASK);
            } else {
                fail(generation, "Не удалось открыть freeform-окно: " + readable(error),
                        error, preset);
            }
        }
    }

    private void assessAndAdopt(
            long generation,
            AdbShellClient shell,
            String dump,
            Preset preset,
            WindowBounds bounds,
            OwnedTask previous) throws Exception {
        Set<Integer> exactIds = TaskOutputParser.taskIdsForComponent(dump, preset.component);
        AppLog.info("Task evidence for " + preset.component + ": exactIds=" + exactIds);
        if (exactIds.isEmpty()) {
            String packageName = packageOf(preset.component);
            Set<Integer> packageIds = TaskOutputParser.taskIdsForPackage(dump, packageName);
            if (!packageIds.isEmpty()) {
                boolean anyExplicitlyWrong = false;
                for (int id : packageIds) {
                    TaskOutputParser.Verification geometry =
                            TaskOutputParser.verifyTaskGeometry(dump, id, bounds);
                    if (isExplicitGeometryFailure(geometry)) anyExplicitlyWrong = true;
                }
                if (anyExplicitlyWrong) {
                    rejectVisibleResult(generation,
                            "Launcher-компонент не подтверждён, а одна из задач приложения "
                                    + "имеет fullscreen/неверные границы",
                            preset);
                    return;
                }
                publish(generation, BackendStatus.State.ACTIVE,
                        preset.label + " • задача видна, но launcher-компонент не подтверждён; "
                                + "управление task отключено",
                        preset, Prefs.NO_TASK);
            } else {
                rejectVisibleResult(generation,
                        "dumpsys не нашёл задачу выбранного приложения", preset);
            }
            return;
        }

        int taskId;
        if (exactIds.size() != 1) {
            Set<Integer> verifiedIds = TaskOutputParser.verifiedTaskIdsForComponent(
                    dump, preset.component, bounds);
            if (verifiedIds.size() == 1) {
                int verifiedTaskId = verifiedIds.iterator().next();
                adopt(generation, shell, preset, bounds, verifiedTaskId, previous);
                return;
            }

            Set<Integer> freeformIds = TaskOutputParser.freeformTaskIdsForComponent(
                    dump, preset.component);
            if (freeformIds.size() == 1) {
                // The OEM may retain a fullscreen task alongside the one direct launch placed in
                // freeform. Exact component + unique freeform mode is sufficient to attempt the
                // same bounds verification/resize used for an otherwise unambiguous task.
                taskId = freeformIds.iterator().next();
            } else {
                boolean allExplicitlyWrong = true;
                for (int id : exactIds) {
                    TaskOutputParser.Verification verification =
                            TaskOutputParser.verifyTask(dump, id, preset.component, bounds);
                    if (!isExplicitGeometryFailure(verification)) {
                        allExplicitlyWrong = false;
                        break;
                    }
                }
                if (allExplicitlyWrong) {
                    rejectVisibleResult(generation,
                            "Несколько задач launcher-компонента имеют fullscreen/неверные границы",
                            preset);
                    return;
                }

                // A successful direct launch remains useful even when the OEM dump cannot identify
                // one task safely. Keep the visible result, but never resize/remove an unowned task.
                prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
                publish(generation, BackendStatus.State.ACTIVE,
                        preset.label + " • найдено несколько подходящих задач; "
                                + "окно оставлено открытым, управление task отключено",
                        preset, Prefs.NO_TASK);
                return;
            }
        } else {
            taskId = exactIds.iterator().next();
        }

        TaskOutputParser.Verification before =
                TaskOutputParser.verifyTask(dump, taskId, preset.component, bounds);
        if (before == TaskOutputParser.Verification.VERIFIED) {
            adopt(generation, shell, preset, bounds, taskId, previous);
            return;
        }
        AdbShellClient.Result resize;
        try {
            resize = shell.execute(ShellCommandBuilder.buildResizeCommand(taskId, bounds));
        } catch (Exception error) {
            if (isExplicitGeometryFailure(before)) {
                rejectVisibleResult(generation,
                        "dumpsys показал неверный режим/границы, а resize не завершён: "
                                + readable(error),
                        preset);
                return;
            }
            throw error;
        }
        if (!resize.ok()) {
            if (before == TaskOutputParser.Verification.INDETERMINATE) {
                publish(generation, BackendStatus.State.ACTIVE,
                        preset.label + " • задача запущена, но режим и границы не удалось проверить",
                        preset, Prefs.NO_TASK);
            } else {
                rejectVisibleResult(generation,
                        "Задача не соответствует freeform-границам, resize отклонён: "
                                + resize.output,
                        preset);
            }
            return;
        }

        String after;
        try {
            after = waitForSpecificTask(shell, preset.component, taskId, bounds);
        } catch (Exception error) {
            if (isExplicitGeometryFailure(before)) {
                rejectVisibleResult(generation,
                        "Исходная задача не была freeform, результат resize проверить не удалось: "
                                + readable(error),
                        preset);
                return;
            }
            throw error;
        }
        if (!isCurrent(generation)) return;
        TaskOutputParser.Verification verification =
                TaskOutputParser.verifyTask(after, taskId, preset.component, bounds);
        if (verification == TaskOutputParser.Verification.VERIFIED) {
            adopt(generation, shell, preset, bounds, taskId, previous);
        } else if (verification == TaskOutputParser.Verification.INDETERMINATE) {
            publish(generation, BackendStatus.State.ACTIVE,
                    preset.label + " • resize выполнен, но результат не подтверждён dumpsys; "
                            + "управление task отключено",
                    preset, Prefs.NO_TASK);
        } else {
            rejectVisibleResult(generation,
                    "Freeform-проверка после resize не пройдена: " + verification, preset);
        }
    }

    private void adopt(
            long generation,
            AdbShellClient shell,
            Preset preset,
            WindowBounds bounds,
            int taskId,
            OwnedTask previous) {
        if (!isCurrent(generation)) return;
        prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, taskId);
        publish(generation, BackendStatus.State.ACTIVE,
                preset.label + " • freeform подтверждён • task " + taskId + " • "
                        + bounds.width() + "×" + bounds.height(),
                preset, taskId);
        removePreviousAfterAdoption(generation, shell, previous, taskId);
    }

    @Override public void resize(WindowBounds bounds) {
        if (bounds == null) throw new NullPointerException("bounds");
        int taskId = prefs.getInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
        Preset active = status.activePreset;
        if (taskId <= 0) {
            if (active == null) {
                long generation = operationGeneration.incrementAndGet();
                fail(generation, "Активная задача не найдена", null, null);
            } else {
                show(active, bounds);
            }
            return;
        }
        if (active == null) {
            long generation = operationGeneration.incrementAndGet();
            prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
            fail(generation, "Владелец активной задачи потерян; resize отменён", null, null);
            return;
        }

        long generation = operationGeneration.incrementAndGet();
        publish(generation, BackendStatus.State.LAUNCHING,
                "Изменение размера " + active.label + "…", active, taskId);
        submit(() -> {
            try (AdbShellClient shell = connected()) {
                String before = readTaskDump(shell, active.component);
                if (!TaskOutputParser.taskHasExactComponent(before, taskId, active.component)) {
                    prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
                    fail(generation, "Task " + taskId
                            + " больше не принадлежит выбранному компоненту; resize отменён",
                            null, active);
                    return;
                }
                if (!isCurrent(generation)) return;
                AdbShellClient.Result result = shell.execute(
                        ShellCommandBuilder.buildResizeCommand(taskId, bounds));
                if (!result.ok()) {
                    fail(generation, "Resize отклонён: " + result.output, null, active);
                    return;
                }
                String after = waitForSpecificTask(shell, active.component, taskId, bounds);
                TaskOutputParser.Verification verification =
                        TaskOutputParser.verifyTask(after, taskId, active.component, bounds);
                if (verification == TaskOutputParser.Verification.VERIFIED) {
                    publish(generation, BackendStatus.State.ACTIVE,
                            "Размер подтверждён • task " + taskId, active, taskId);
                } else {
                    prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
                    fail(generation, "Resize не подтверждён: " + verification, null, active);
                }
            } catch (Exception error) {
                fail(generation, "Не удалось изменить окно: " + readable(error), error, active);
            }
        });
    }

    @Override public void hide() {
        long generation = operationGeneration.incrementAndGet();
        int taskId = prefs.getInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
        Preset active = status.activePreset;
        try {
            runOnMain(() -> DirectFreeformLauncher.goHome(context));
        } catch (Exception error) {
            AppLog.warn("Cannot return to HOME", error);
        }
        prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
        if (taskId <= 0 || active == null) {
            publish(generation, BackendStatus.State.IDLE,
                    "Окно скрыто", null, Prefs.NO_TASK);
            return;
        }
        // IDLE is intentionally delayed: OverlayService stops itself on IDLE and would otherwise
        // interrupt the positively-owned task cleanup immediately.
        publish(generation, BackendStatus.State.LAUNCHING,
                "Окно скрыто; проверка задачи перед закрытием…", active, taskId);
        submit(() -> removeOwnedTask(generation, taskId, active));
    }

    private void removeOwnedTask(long generation, int taskId, Preset owner) {
        if (!isCurrent(generation)) return;
        try (AdbShellClient shell = connected()) {
            String before = readTaskDump(shell, owner.component);
            if (!TaskOutputParser.taskHasExactComponent(before, taskId, owner.component)) {
                publish(generation, BackendStatus.State.IDLE,
                        "Окно скрыто; task уже отсутствует или сменил владельца",
                        null, Prefs.NO_TASK);
                return;
            }
            if (!isCurrent(generation)) return;
            AdbShellClient.Result remove = shell.execute(
                    ShellCommandBuilder.buildRemoveCommand(taskId));
            if (!remove.ok()) {
                publish(generation, BackendStatus.State.IDLE,
                        "Окно скрыто; Android не удалил task " + taskId,
                        null, Prefs.NO_TASK);
                return;
            }
            String after = readTaskDump(shell, owner.component);
            publish(generation, BackendStatus.State.IDLE,
                    TaskOutputParser.containsTask(after, taskId)
                            ? "Окно скрыто; task " + taskId + " остался в списке задач"
                            : "Окно закрыто",
                    null, Prefs.NO_TASK);
        } catch (Exception error) {
            AppLog.warn("Optional task removal failed", error);
            publish(generation, BackendStatus.State.IDLE,
                    "Окно скрыто; закрытие task не подтверждено",
                    null, Prefs.NO_TASK);
        }
    }

    @Override public BackendStatus status() { return status; }

    @Override public void close() {
        closed = true;
        operationGeneration.incrementAndGet();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private AdbShellClient connected() throws Exception {
        AdbShellClient client = new AdbShellClient(context);
        try {
            client.connect(prefs.getInt(Prefs.KEY_ADB_PORT, Prefs.DEFAULT_ADB_PORT));
            return client;
        } catch (Exception error) {
            client.close();
            throw error;
        }
    }

    private String waitForTaskEvidence(AdbShellClient shell, String component) throws Exception {
        String packageName = packageOf(component);
        String lastSuccessful = null;
        String lastEvidence = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            String dump = readTaskDump(shell, component);
            lastSuccessful = dump;
            if (!TaskOutputParser.taskIdsForComponent(dump, component).isEmpty()
                    || !TaskOutputParser.taskIdsForPackage(dump, packageName).isEmpty()) {
                // OEM ActivityTaskManager can briefly report both the old and the newly launched
                // task. Keep sampling and assess the final evidence instead of adopting or
                // rejecting a transient first snapshot.
                lastEvidence = dump;
            }
            if (attempt + 1 < 8) Thread.sleep(250L);
        }
        if (lastEvidence != null) return lastEvidence;
        return lastSuccessful == null ? "" : lastSuccessful;
    }

    private String waitForSpecificTask(
            AdbShellClient shell,
            String component,
            int taskId,
            WindowBounds expectedBounds) throws Exception {
        String last = "";
        for (int attempt = 0; attempt < 6; attempt++) {
            last = readTaskDump(shell, component);
            if (TaskOutputParser.verifyTask(last, taskId, component, expectedBounds)
                    == TaskOutputParser.Verification.VERIFIED) return last;
            Thread.sleep(200L);
        }
        return last;
    }

    private String readTaskDump(AdbShellClient shell, String component) throws Exception {
        AdbShellClient.Result activities = shell.execute(
                ShellCommandBuilder.buildProbeCommand(packageOf(component)));
        if (activities.ok()
                && (!TaskOutputParser.taskIdsForComponent(activities.output, component).isEmpty()
                || !TaskOutputParser.taskIdsForPackage(
                        activities.output, packageOf(component)).isEmpty())) {
            return activities.output;
        }
        AdbShellClient.Result stacks = shell.execute(
                ShellCommandBuilder.buildStackListProbeCommand());
        if (stacks.ok()) {
            return activities.ok() ? activities.output + "\n" + stacks.output : stacks.output;
        }
        if (!activities.ok()) {
            throw new IllegalStateException("dumpsys/am stack failed: " + stacks.output);
        }
        return activities.output;
    }

    private void launchDirect(long generation, Preset preset, WindowBounds bounds) throws Exception {
        runOnMain(() -> {
            if (!isCurrent(generation)) return;
            // HOME must become the surface below the freeform task before it is launched.
            // Pressing HOME afterwards backgrounds the freeform task on Android 11.
            DirectFreeformLauncher.goHome(context);
            if (isCurrent(generation)) DirectFreeformLauncher.launch(context, preset, bounds);
        });
    }

    private void restoreHomeAfterStaleLaunch() {
        if (status.state != BackendStatus.State.IDLE) return;
        try {
            runOnMain(() -> DirectFreeformLauncher.goHome(context));
        } catch (Exception error) {
            AppLog.warn("Cannot restore HOME after stale shell launch", error);
        }
    }

    private OwnedTask ownedTaskFromCurrentStatus() {
        int taskId = prefs.getInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
        BackendStatus current = status;
        if (taskId <= 0 || current.state != BackendStatus.State.ACTIVE
                || current.activePreset == null || current.taskId != taskId) {
            return null;
        }
        return new OwnedTask(taskId, current.activePreset);
    }

    private void removePreviousAfterAdoption(
            long generation, AdbShellClient shell, OwnedTask previous, int adoptedTaskId) {
        if (previous == null || previous.taskId == adoptedTaskId || !isCurrent(generation)) return;
        try {
            String dump = readTaskDump(shell, previous.preset.component);
            if (!TaskOutputParser.taskHasExactComponent(
                    dump, previous.taskId, previous.preset.component)) {
                AppLog.info("Previous task is already absent or changed owner: " + previous.taskId);
                return;
            }
            if (!isCurrent(generation)) return;
            AdbShellClient.Result remove = shell.execute(
                    ShellCommandBuilder.buildRemoveCommand(previous.taskId));
            if (!remove.ok()) {
                AppLog.info("Previous Atlas task remained in recents: " + remove.output);
            }
        } catch (Exception error) {
            AppLog.warn("Cannot remove previous Atlas-owned task", error);
        }
    }

    private void runOnMain(Runnable runnable) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        main.post(() -> {
            try { runnable.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        if (!done.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("main thread timeout");
        Throwable error = failure.get();
        if (error instanceof Exception) throw (Exception) error;
        if (error != null) throw new IllegalStateException(error);
    }

    private void submit(Runnable work) {
        if (closed) return;
        try {
            worker.execute(work);
        } catch (RejectedExecutionException ignored) {
            // close() won the race; no work may publish after close.
        }
    }

    private void rejectVisibleResult(long generation, String message, Preset preset) {
        if (!isCurrent(generation)) return;
        prefs.putInt(Prefs.KEY_ACTIVE_TASK_ID, Prefs.NO_TASK);
        try {
            runOnMain(() -> DirectFreeformLauncher.goHome(context));
        } catch (Exception error) {
            AppLog.warn("Cannot return to HOME after failed freeform verification", error);
        }
        fail(generation, message, null, preset);
    }

    private void fail(long generation, String message, Throwable error, Preset preset) {
        if (error != null) AppLog.warn(message, error);
        publish(generation, BackendStatus.State.ERROR, message, preset, Prefs.NO_TASK);
    }

    private void publish(
            long generation, BackendStatus.State state, String detail, Preset preset, int taskId) {
        if (!isCurrent(generation)) return;
        BackendStatus next = new BackendStatus(state, detail, preset, taskId);
        status = next;
        main.post(() -> {
            if (isCurrent(generation) && status == next) listener.onBackendStatus(next);
        });
    }

    private void publishProbe(long generation, BackendStatus.State state, String detail) {
        if (!mayPublishProbe(generation)) return;
        BackendStatus next = new BackendStatus(state, detail, null, Prefs.NO_TASK);
        status = next;
        main.post(() -> {
            if (mayPublishProbe(generation) && status == next) listener.onBackendStatus(next);
        });
    }

    private boolean mayPublishProbe(long generation) {
        if (!isCurrent(generation)) return false;
        BackendStatus.State state = status.state;
        return state != BackendStatus.State.LAUNCHING && state != BackendStatus.State.ACTIVE;
    }

    private boolean isCurrent(long generation) {
        return !closed && operationGeneration.get() == generation;
    }

    private static String packageOf(String component) {
        return component.substring(0, component.indexOf('/'));
    }

    private static boolean isExplicitGeometryFailure(TaskOutputParser.Verification verification) {
        return verification == TaskOutputParser.Verification.WRONG_MODE
                || verification == TaskOutputParser.Verification.WRONG_BOUNDS;
    }

    private static String readable(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static final class OwnedTask {
        final int taskId;
        final Preset preset;

        OwnedTask(int taskId, Preset preset) {
            this.taskId = taskId;
            this.preset = preset;
        }
    }
}
