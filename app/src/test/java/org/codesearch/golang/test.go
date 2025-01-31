package main

import (
 "context"
 "errors"
 "fmt"
 "math/rand"
 "sync"
 "time"
)

// Определяем структуру для представления человека
type Person struct {
 Name string
 Age  int
}

// Интерфейс, который будет реализован структурой Person
type Greeter interface {
 Greet() string
}

// Метод для структуры Person, реализующий интерфейс Greeter
func (p Person) Greet() string {
 return fmt.Sprintf("Hello, my name is %s and I am %d years old.", p.Name, p.Age)
}

// Функция для генерации случайных людей
func generateRandomPeople(ctx context.Context, n int, results chan<- Person, errChan chan<- error) {
 defer close(results)

 names := []string{"Alice", "Bob", "Charlie", "Diana", "Ethan"}
 if n <= 0 {
  errChan <- errors.New("number of people must be greater than 0")
  return
 }

 for i := 0; i < n; i++ {
  select {
  case <-ctx.Done():
   return // Выход из функции, если контекст отменен
  default:
   person := Person{
    Name: names[rand.Intn(len(names))],
    Age:  rand.Intn(100), // Случайный возраст от 0 до 99
   }
   results <- person
  }
 }
}

// Функция для обработки людей
func processPeople(results <-chan Person, errChan <-chan error, wg *sync.WaitGroup) {
 defer wg.Done()
 for {
  select {
  case person, ok := <-results:
   if !ok {
    return // Результаты канала закрыты
   }
   // Взаимодействие с интерфейсом
   fmt.Println(person.Greet())
  case err := <-errChan:
   if err != nil {
    fmt.Printf("Error: %s\n", err)
   }
  }
 }
}

func main() {
 rand.Seed(time.Now().UnixNano()) // Инициализируем генератор случайных чисел
 const numPeople = 5

 // Создаем каналы для передачи людей и ошибок
 results := make(chan Person, numPeople)
 errChan := make(chan error, 1)

 // Создаем контекст с функцией отмены
 ctx, cancel := context.WithCancel(context.Background())
 defer cancel() // Вызываем отмену в конце программы

 var wg sync.WaitGroup

 // Запускаем горутину для генерации людей
 wg.Add(1)
 go func() {
  defer wg.Done()
  generateRandomPeople(ctx, numPeople, results, errChan)
 }()

 // Запускаем горутину для обработки сгенерированных людей
 wg.Add(1)
 go processPeople(results, errChan, &wg)

 // Ждем завершения всех горутин и отменяем контекст, если все завершилось
 wg.Wait()

 // Используем switch для демонстрации
 ageGroup := "unknown"
 for _, person := range []Person{
  {Name: "Alice", Age: 15},
  {Name: "Bob", Age: 35},
  {Name: "Charlie", Age: 60},
 } {
  switch {
  case person.Age < 18:
   ageGroup = "minor"
  case person.Age < 65:
   ageGroup = "adult"
  default:
   ageGroup = "senior"
  }
  fmt.Printf("%s is an %s.\n", person.Name, ageGroup)
 }
}