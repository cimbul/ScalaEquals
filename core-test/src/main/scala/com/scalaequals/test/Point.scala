package com.scalaequals.test

import com.scalaequals.Equals

class Point(val x: Int, val y: Int, private val z: Int) {
  override def hashCode: Int = 41 * (41 * (41 + x) + y) + z
  override def equals(other: Any): Boolean = Equals.equal(other)
  def canEqual(other: Any): Boolean = other.isInstanceOf[Point]
}

object Color extends Enumeration {
  val Red, Orange, Yellow, Green, Blue, Indigo, Violet = Value
}

class ColoredPoint(x: Int, y: Int, z: Int, val color: Color.Value) extends Point(x, y, z) {
  override def hashCode: Int = 41 * super.hashCode + color.hashCode
  override def equals(other: Any): Boolean = Equals.equalC(other)
  override def canEqual(other: Any): Boolean = other.isInstanceOf[ColoredPoint]
}

class FourDColoredPoint(w: Int, x: Int, y: Int, z: Int, color: Color.Value) extends ColoredPoint(x, y, z, color) {
  override def hashCode: Int = 41 * super.hashCode + color.hashCode
  override def equals(other: Any): Boolean = Equals.equal(other)
  override def canEqual(other: Any): Boolean = other.isInstanceOf[FourDColoredPoint]
}