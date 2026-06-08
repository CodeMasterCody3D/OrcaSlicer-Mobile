package ru.ytkab0bp.slicebeam.utils;

public final class FillBedPlanner {
    private FillBedPlanner() {}

    public static int copyAttemptsForLimit(int currentObjectCount, int maxObjectCount) {
        if (currentObjectCount <= 0 || maxObjectCount <= currentObjectCount) {
            return 0;
        }
        return maxObjectCount - currentObjectCount;
    }
}
