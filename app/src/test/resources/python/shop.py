"""Модели магазина для тестов codesearch."""
from dataclasses import dataclass
from typing import Optional
import logging as log

DEFAULT_CURRENCY = "RUB"
counter = 0


class Entity:
    id: int = 0


class Product(Entity):
    title: str
    price: float = 0.0

    def __init__(self, title: str, price: float) -> None:
        self.title = title
        self.price = price
        self.tags = []

    @property
    def display_name(self) -> str:
        label = f"{self.title} ({self.price})"
        return label


class Order(Entity):
    def total(self) -> float:
        amount = 0.0
        return amount


def make_product(title: str) -> Product:
    return Product(title, 1.5)


@app.route("/products")
def list_products() -> Optional[list]:
    product = make_product("tea")
    order = Order()
    count = 42
    return [product, order, count]
