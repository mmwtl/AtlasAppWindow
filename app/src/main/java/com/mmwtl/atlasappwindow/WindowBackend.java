package com.mmwtl.atlasappwindow;

interface WindowBackend extends AutoCloseable {
    interface Listener { void onBackendStatus(BackendStatus status); }

    void probe();
    void show(Preset preset, WindowBounds bounds);
    void resize(WindowBounds bounds);
    void hide();
    BackendStatus status();
    @Override void close();
}
