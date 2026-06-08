package ru.ytkab0bp.slicebeam.boot;

import ru.ytkab0bp.eventbus.EventBus;
public class EventBusTask extends BootTask {

    public EventBusTask() {
        // The annotation processor generates the EventBus implementation from the
        // Java namespace, not the Android applicationId. OrcaSlicer Mobile keeps
        // the original Java namespace during bootstrap so JNI method names and
        // generated event handlers remain compatible while applicationId changes.
        super(() -> EventBus.registerImpl("ru.ytkab0bp.slicebeam"));
        onWorker();
    }
}
