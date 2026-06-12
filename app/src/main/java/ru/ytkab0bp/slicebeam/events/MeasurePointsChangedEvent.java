package ru.ytkab0bp.slicebeam.events;

import ru.ytkab0bp.slicebeam.utils.Vec3d;

public class MeasurePointsChangedEvent {
    public final Vec3d pointA;
    public final Vec3d pointB;

    public MeasurePointsChangedEvent(Vec3d pointA, Vec3d pointB) {
        this.pointA = pointA;
        this.pointB = pointB;
    }
}
