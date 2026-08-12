package com.mmwtl.atlasappwindow;

final class BackendStatus {
    enum State { IDLE, CONNECTING, READY, LAUNCHING, ACTIVE, ERROR }

    final State state;
    final String detail;
    final Preset activePreset;
    final int taskId;

    BackendStatus(State state, String detail, Preset activePreset, int taskId) {
        this.state = state;
        this.detail = detail;
        this.activePreset = activePreset;
        this.taskId = taskId;
    }
}
