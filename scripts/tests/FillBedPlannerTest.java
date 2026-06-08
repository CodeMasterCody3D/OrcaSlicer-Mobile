import ru.ytkab0bp.slicebeam.utils.FillBedPlanner;

public class FillBedPlannerTest {
    public static void main(String[] args) {
        assertEquals(0, FillBedPlanner.copyAttemptsForLimit(0, 256), "zero objects should not add copies");
        assertEquals(255, FillBedPlanner.copyAttemptsForLimit(1, 256), "one object can attempt 255 more copies");
        assertEquals(1, FillBedPlanner.copyAttemptsForLimit(255, 256), "cap should allow one final attempt");
        assertEquals(0, FillBedPlanner.copyAttemptsForLimit(256, 256), "at cap should not add copies");
        assertEquals(0, FillBedPlanner.copyAttemptsForLimit(300, 256), "above cap should not add copies");
        assertEquals(0, FillBedPlanner.copyAttemptsForLimit(1, 1), "cap of one means no extra copies");
        System.out.println("FillBedPlannerTest passed");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
