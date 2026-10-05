package shapes

type Shape interface {
	Area() float64
}

type Solid interface {
	Shape
	Volume() float64
}

type Square struct {
	side float64
}

func (s Square) Area() float64 {
	return s.side * s.side
}

type Cube struct {
	side float64
}

func (c Cube) Area() float64 {
	return 6 * c.side * c.side
}

func (c Cube) Volume() float64 {
	return c.side * c.side * c.side
}

func Describe(shape Shape) float64 {
	return shape.Area()
}
