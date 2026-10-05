package samples

type Greeter interface {
	Greet(name string) string
}

type Service struct {
	host string
	port int
}

func NewService(host string, port int) *Service {
	return &Service{host: host, port: port}
}

func (s *Service) Address() string {
	return s.host
}

func main() {
	svc := NewService("localhost", 8080)
	_ = svc.Address()
	message := "hello"
	count := 42
	_ = message
	_ = count
}
