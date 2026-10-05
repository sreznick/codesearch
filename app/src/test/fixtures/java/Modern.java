package demo.shapes;

import java.util.List;
import java.util.function.Function;

sealed interface Shape permits Circle, Square {
    double area();
}

interface Labeled {
    String label();
}

record Circle(double radius) implements Shape {
    public Circle {
        validate(radius);
    }

    public double area() {
        return Math.PI * radius * radius;
    }

    private static void validate(double value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
    }
}

non-sealed class Square implements Shape, Labeled {
    private final double side;
    private int width, height;

    Square(double side) {
        this.side = side;
    }

    @Override
    public double area() {
        return side * side;
    }

    @Override
    public String label() {
        return """
                square
                """;
    }
}

class Cube extends Square {
    Cube(double side) {
        super(side);
    }
}

enum Color implements Labeled {
    RED, GREEN;

    public String label() {
        return name().toLowerCase();
    }
}

class Report {
    double total(List<Shape> shapes, @Deprecated String title) {
        Function<Shape, Double> area = Shape::area;
        double sum = shapes.stream().mapToDouble(shape -> area.apply(shape)).sum();
        for (var shape : shapes) {
            String kind = switch (shape) {
                case Circle circle -> "circle";
                case Square square -> "square";
            };
            System.out.println(kind);
        }
        var cube = new Cube(2);
        return sum + cube.area();
    }
}
