public interface TestInterface {
    void doSomething();
    void doSomething(Object arg);
}

public class TestClass {
    private String testField = "Test String";
    private int testInt = 12345;
    private float testFloat = 123.45;
    private char testChar = 'A';
    private boolean testBool = true;
    private String testFieldDuplicate = "Test String";

    public void testMethod() {
        String localVariable = "Test Local Variable";
        System.out.println("Test Method called");
    }

    public void testMethodWithReturn() {
        String localVariable = "Test local variable";
        return;
    }

    public void testMethod(String msg) {
        System.out.println(msg);
        int val = 12345;
        float x = 123.45;
    }

    public String getTestField() {
        return testField;
    }

    private String testFieldforMaxOutput = "max_out";

    private String testFieldforMaxOutput2 = "max_out";
}

