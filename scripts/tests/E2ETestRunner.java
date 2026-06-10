package scripts.tests;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class E2ETestRunner {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("        ORCASLICERMOBILE HEADLESS E2E RUNNER      ");
        System.out.println("==================================================");

        List<TestResult> results = new ArrayList<>();
        Method[] methods = E2ETestSuite.class.getDeclaredMethods();

        int passed = 0;
        int failed = 0;

        for (Method m : methods) {
            if (m.getName().startsWith("test")) {
                System.out.print("Running " + m.getName() + " ... ");
                try {
                    m.invoke(null);
                    System.out.println("PASS");
                    results.add(new TestResult(m.getName(), true, null));
                    passed++;
                } catch (Exception e) {
                    System.out.println("FAIL");
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    results.add(new TestResult(m.getName(), false, cause.toString()));
                    failed++;
                }
            }
        }

        System.out.println("\n--- DETAILED TEST EXECUTION REPORT ---");
        for (TestResult r : results) {
            if (r.passed) {
                System.out.println("[PASS] " + r.name);
            } else {
                System.out.println("[FAIL] " + r.name + ": " + r.errorReason);
            }
        }

        System.out.println("\n==================================================");
        System.out.println("                 TEST SUMMARY                     ");
        System.out.println("==================================================");
        System.out.println("  TOTAL TESTS RUN : " + (passed + failed));
        System.out.println("  PASSED          : " + passed);
        System.out.println("  FAILED          : " + failed);
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static class TestResult {
        String name;
        boolean passed;
        String errorReason;

        TestResult(String name, boolean passed, String errorReason) {
            this.name = name;
            this.passed = passed;
            this.errorReason = errorReason;
        }
    }
}
