package sample

import (
    "fmt"
    "strings"
)

const bitsPerWord = 64

func intMin(a, b int) int {
    if a < b {
        return a
    }
    return b
}

type BitSet struct {
    data []uint64
    name string
}

type Printer interface {
    Print(value string) error
}

var defaultName string = "main"

func (b *BitSet) Add(value int) {
    fmt.Println(strings.TrimSpace(defaultName), value)
}
