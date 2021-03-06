/*
 * Copyright (c) 2013 Alex DiCarlo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.scalaequals.test

import org.scalatest.{GivenWhenThen, FeatureSpec}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalacheck.Gen
import scala.annotation.tailrec

/**
 * There are 5 generators used to test equals/canEqual/hashCode:
 *
 *  - Single example generator
 *
 *  - Equal pair example generator that are different in all fields NOT checked by equality
 *
 *  - Unequal pair example generator that are different in ONLY 1 field that is checked by equality. Same in all other
 *
 *  - Equal triplet example generator that are different in all fields NOT checked by equality
 *
 *  - Unequal triplet example generator. Creates 2 unequal examples that are different in ONLY 1 field that is checked
 *  by equality, then creates 2 equal examples that are different in all fields NOT checked by equality
 *
 * Tests reflexive prop with single example generator
 *
 * Tests symmetric prop with equal pair example generator and unequal pair example generator
 *
 * Tests transitive prop with equal triplet example generator and unequal triplet example generator
 *
 * Tests x.equals(null) == false with single example generator
 *
 * Tests hashCode consistency with pair example generator
 *
 * @tparam A To be tested
 * @tparam B Case class representing arguments of A
 */
trait EqualsFixture[A, B] extends FeatureSpec with
                                  GivenWhenThen with
                                  ShouldMatchers with
                                  GeneratorDrivenPropertyChecks {
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 1000)

  def name: String

  def gen: Gen[B]

  /* Creates a A from B */
  def create(arg: B): A

  /* Creates a String to test toString = A(arg) */
  def createToString(arg: B): String

  /* Swaps all constructor arguments that are not part of equals from arg to arg2's values */
  def changeDiff(arg: B, arg2: B): B

  /* Changes one random argument that is part of equals to arg2's value */
  def changeRandom(arg: B, arg2: B): B

  /* true if arg and arg2 differ in a field not checked by equality or there are no fields that can differ */
  def diff(arg: B, arg2: B): Boolean

  /* true if arg and arg2 differ in a field checked by equality */
  def unequal(arg: B, arg2: B): Boolean

  @tailrec
  final def swap[B](original: IndexedSeq[B], change: IndexedSeq[B]): IndexedSeq[B] = {
    val swapIdx = util.Random.nextInt(original.size)
    val swapped = original.updated(swapIdx, change(swapIdx))
    if (swapped != original) swapped else swap(original, change)
  }

  @tailrec
  private final def ensuredChangeRandom(arg: B, arg2: B): B = {
    val newArg = changeRandom(arg, arg2)
    if (unequal(arg, newArg)) newArg else ensuredChangeRandom(arg, arg2)
  }

  def classGen: Gen[A] = for {
    arg <- gen
  } yield create(arg)

  def equal2ClassGen: Gen[(A, A)] = for {
    arg <- gen
    arg2 <- gen suchThat {a => diff(a, arg)}
  } yield (create(arg), create(changeDiff(arg, arg2)))

  def unequal2ClassGen: Gen[(A, A)] = for {
    arg <- gen
    arg2 <- gen suchThat {a => unequal(a, arg)}
  } yield (create(arg), create(ensuredChangeRandom(arg, arg2)))

  def equal3ClassGen: Gen[(A, A, A)] = for {
    arg <- gen
    arg2 <- gen suchThat {a => diff(a, arg)}
    arg3 <- gen suchThat {a => diff(a, arg) && diff(a, arg2)}
  } yield (create(arg), create(changeDiff(arg, arg2)), create(changeDiff(arg, arg3)))

  def unequal3ClassGen: Gen[(A, A, A)] = for {
    arg <- gen
    arg2 <- gen suchThat {a => unequal(a, arg)}
    arg3 <- gen suchThat {a => unequal(a, arg) && unequal(a, arg2)}
    unequalArg = ensuredChangeRandom(arg, arg2)
    diffArg = changeDiff(unequalArg, arg3)
  } yield (create(arg), create(unequalArg), create(diffArg))

  feature("ScalaEquals is Reflexive") {
    scenario(s"$name") {
      Given("any non-null value x")
      When("x.equals(x)")
      Then("the result is true")
      forAll(classGen) {x =>
        x.equals(x) should be(true)
      }
    }
  }

  feature("ScalaEquals is Symmetric") {
    scenario(s"$name") {
      Given("any non-null values x and y")
      When("x.equals(y) returns true (returns false)")
      Then("y.equals(x) returns true (returns false)")
      forAll(Gen.containerOfN[List, A](2, classGen)) {xs =>
        val x = xs(0)
        val y = xs(1)
        x.equals(y) should equal(y.equals(x))
      }
    }

    scenario(s"Equal pairs of $name") {
      Given("2 equal non-null values x and y")
      Then("x.equals(y) and y.equals(x) is true")
      forAll(equal2ClassGen) {
        case (x, y) =>
          x.equals(y) should be(true)
          y.equals(x) should be(true)
      }
    }

    scenario(s"Unequal pairs of $name") {
      Given("2 unequal non-null values x and y")
      Then("x.equals(y) and y.equals(x) is false")
      forAll(unequal2ClassGen) {
        case (x, y) =>
          x.equals(y) should be(false)
          y.equals(x) should be(false)
      }
    }
  }

  feature("ScalaEquals is Transitive") {
    scenario(s"$name") {
      Given("any non-null values x, y, and z")
      When("x.equals(y) is true and y.equals(z) is true ")
      Then("x.equals(z) is true")
      forAll(Gen.containerOfN[List, A](3, classGen)) {cps =>
        val x = cps(0)
        val y = cps(1)
        val z = cps(2)
        if (x.equals(y) && y.equals(z))
          x.equals(z) should be(true)
      }
    }

    scenario(s"Equal triples of $name") {
      Given("any non-null values x, y, and z")
      When("x.equals(y) is true and y.equals(z) is true ")
      Then("x.equals(z) is true")
      forAll(equal3ClassGen) {
        case (x, y, z) =>
          x.equals(y) should be(true)
          y.equals(z) should be(true)
          x.equals(z) should be(true)
      }
    }

    scenario(s"Unequal triples of $name") {
      Given("any non-null values x, y, and z")
      When("x.equals(y) is false and y.equals(z) is true ")
      def testFirst(x: A, y: A, z: A) {
        x.equals(y) should be(false)
        y.equals(z) should be(true)
      }
      Then("x.equals(z) is false")
      def testSecond(x: A, z: A) {
        x.equals(z) should be(false)
      }
      forAll(unequal3ClassGen) {
        case (x, y, z) =>
          testFirst(x, y, z)
          testSecond(x, z)
      }
    }
  }

  feature("ScalaEquals with null") {
    scenario(s"$name") {
      Given("any non-null value x")
      When("x.equals(null)")
      Then("the result is false")
      forAll(classGen) {
        x =>
          x.equals(null) should be(false)
      }
    }
  }

  feature("ScalaEquals is consistent with hashCode") {
    scenario(s"Equal pairs of $name") {
      Given("any non-null values x and y")
      When("x.equals(y) returns true")
      Then("x.hashCode == y.hashCode")
      forAll(equal2ClassGen) {
        case (x, y) =>
          x.hashCode() should equal(y.hashCode())
      }
    }
  }

  feature("ScalaEquals generates strings from constructor arguments") {
    scenario(s"$name.toString") {
      Given(s"a $name")
      When("toString is called")
      Then(s"the result is $name(args)")
      forAll(gen) {
        arg =>
          val a = create(arg)
          a.toString should equal(createToString(arg))
      }
    }
  }
}

trait SubClassedEqualsFixture[A, B, C <: A] {self: EqualsFixture[A, B] =>
  def subClassName: String

  /* Creates a C, a subclass of A, from B */
  def createSubClass(arg: B): C

  def subClassGen: Gen[C] = for {
    arg <- gen
  } yield createSubClass(arg)

  def equal2SubClassGen: Gen[(A, C)] = for {
    arg <- gen
  } yield (create(arg), createSubClass(arg))

  feature("ScalaEquals is consistent with subclassing") {
    scenario(s"Same arg generated pairs of $name and $subClassName") {
      Given(s"any non-null values x of $name and y of $subClassName created with the same arg")
      When("x.equals(y) returns true (returns false)")
      Then("y.equals(x) returns true (returns false)")
      forAll(equal2SubClassGen) {case (x, y) =>
        x.equals(y) should equal(y.equals(x))
      }
    }

    scenario(s"$name and $subClassName are symmetric") {
      Given("any non-null values x and y")
      When("x.equals(y) returns true (returns false)")
      Then("y.equals(x) returns true (returns false)")
      forAll(Gen.sequence[List, A](Seq(classGen, subClassGen))) {xs =>
        val x = xs(0)
        val y = xs(1)
        x.equals(y) should equal(y.equals(x))
      }
    }

    scenario(s"$name and $subClassName are transitive") {
      Given("any non-null values x and y")
      When("x.equals(y) and y.equals(z) returns true")
      Then("x.equals(z) returns true")
      forAll(Gen.containerOfN[List, A](3, Gen.oneOf[A](classGen, subClassGen))) {xs =>
        val x = xs(0)
        val y = xs(1)
        val z = xs(2)
        if (x.equals(y) && y.equals(z))
          x.equals(z) should be(true)
      }
    }
  }
}
