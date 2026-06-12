package ru.ytkab0bp.slicebeam.events;

import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class EmbossSurfaceClickedEvent {
    public final Vec3d position;
    public final Vec3d normal;

    public EmbossSurfaceClickedEvent(Vec3d position, Vec3d normal) {
        this.position = position;
        this.normal = normal;
    }
}
