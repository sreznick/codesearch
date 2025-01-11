# Testing the Codesearch itself. Search for 'class' or 'literal'

a = "some string literal"

class WithDocstring:
    """this class is""" """ documented."""


literal = False
documented = False

"""
We love multiline
literals.
"""

# Overview of things not supported by the old lexer/parser

# walrus operator (Python 3.8+)
if (n := len([1, 2, 3])) > 2:
    print(f"The list has {n} elements.")

# positional-only parameters (Python 3.8+)
def greet(name, /, age):
    print(f"Hello, {name}! You are {age} years old.")

# Exception Groups (Python 3.11+)
try:
    raise ExceptionGroup(
        "Multiple errors occurred",
        [ValueError("Invalid value"), TypeError("Invalid type")]
    )
except* ValueError as ve:
    print(f"ValueError: {ve}")
except* TypeError as te:
    print(f"TypeError: {te}")

# Parenthesized Context Managers (Python 3.10+)
with (
    open("example.txt", "w") as file1,
    open("example2.txt", "w") as file2
):
    file1.write("Hello, world!")
    file2.write("Hello again!")

# Walrus Operator in List Comprehensions (Python 3.8+)
numbers = [1, 2, 3, 4, 5]
squared_numbers = [x**2 for x in numbers if (n := x) % 2 == 0]
print(squared_numbers)
