package com.buzuli.collections

import com.buzuli.UnitSpec

import scala.util.{Failure, Try}

class FlipListSpec extends UnitSpec {
  "FlipList" when {
    "list is empty" should {
      "left should be None" in {
        FlipList().left shouldBe None
      }

      "right should be None" in {
        FlipList().right shouldBe None
      }

      "isFlipped should be false by default" in {
        FlipList().isFlipped shouldBe false
      }

      "isFlipped should be true after flip() even when empty" in {
        FlipList().flip().isFlipped shouldBe true
      }
    }

    "list contains a single element" should {
      "left should be the same node as right" in {
        Some(FlipList("a")) foreach { l =>
          l.left.map(_.value) shouldBe Some("a")
          l.left shouldBe l.right
        }

        Some(FlipList[String]()) foreach { l =>
          l.pushLeft("a")
          l.pushLeft("b")
          l.popRight() shouldBe Some("a")
          l.left.map(_.value) shouldBe Some("b")
          l.right.map(_.value) shouldBe Some("b")
          l.left shouldBe l.right
        }
      }
    }

    "list contains multiple elements" should {
      "honor the initialization order" in {
        Some(FlipList("a", "b", "c")) foreach { l =>
          l.popLeft() shouldBe Some("a")
          l.popLeft() shouldBe Some("b")
          l.popLeft() shouldBe Some("c")
        }
      }

      "traverse in reverse order after flip()" in {
        Some(FlipList("a", "b", "c").flip()) foreach { l =>
          l.popLeft() shouldBe Some("c")
          l.popLeft() shouldBe Some("b")
          l.popLeft() shouldBe Some("a")
        }
      }

      "correctly switch directions mid traversal" in {
        Some(FlipList("a", "b", "c")) foreach { l =>
          val b = l.left.flatMap(_.right)
          b.map(_.value) shouldBe Some("b")

          b.flatMap(_.left).map(_.value) shouldBe Some("a")
          b.flatMap(_.right).map(_.value) shouldBe Some("c")

          l.flip()

          b.flatMap(_.left).map(_.value) shouldBe Some("c")
          b.flatMap(_.right).map(_.value) shouldBe Some("a")
        }
      }
    }

    "adding values to the list" should {
      "be able to add an element before the current node" in {
        Some(FlipList("b")) foreach { l =>
          val b = l.left
          b.map(_.addToLeft("a"))
          l.popLeft() shouldBe Some("a")
          l.popLeft() shouldBe Some("b")
        }
      }

      "be able to add an element after the current node" in {
        Some(FlipList("a")) foreach { l =>
          val a = l.right
          a.map(_.addToRight("b"))
          l.popLeft() shouldBe Some("a")
          l.popLeft() shouldBe Some("b")
        }
      }

      "be able to add a value to the left of the list" in {
        Some(FlipList("b")) foreach { l =>
          l.pushLeft("a")
          l.popRight() shouldBe Some("b")
          l.popRight() shouldBe Some("a")
        }
      }

      "be able to add a value to the right of the list" in {
        Some(FlipList("a")) foreach { l =>
          l.pushRight("b")
          l.popLeft() shouldBe Some("a")
          l.popLeft() shouldBe Some("b")
        }
      }
    }

    "removing values from the list" should {
      "only permit referencing a node's value after it has been removed from the list" in {
        Some(FlipList("a")) foreach { l =>
          val a = l.left
          val b = a.map(_.addToRight("b"))
          a.map(_.isInList) shouldBe Some(true)
          a.map(_.remove())
          a.map(_.isInList) shouldBe Some(false)
          Try(a.map(_.addToRight("c"))) shouldBe Failure(DecommissionedNodeException)
          l.left shouldBe b
          l.right shouldBe b
        }
      }
    }
  }

  "used as a stack" should {
    "honor isFlipped state when push() is called" in {
      Some(FlipList[String]()) foreach { l =>
        l.push("a")
        l.flip()
        l.push("b")
        l.flip()
        l.push("c")
        l.flip()
        l.push("d")
        l.flip()
        l.push("e")

        l.pop() shouldBe Some("e")
        l.pop() shouldBe Some("c")
        l.pop() shouldBe Some("a")
        l.pop() shouldBe Some("b")
        l.pop() shouldBe Some("d")
        l.pop() shouldBe None
      }
    }

    "honor isFlipped state when pop() is called" in {
      Some(FlipList[String]()) foreach { l =>
        l.push("a")
        l.push("b")
        l.push("c")
        l.push("d")
        l.push("e")

        l.pop() shouldBe Some("e")
        l.flip()
        l.pop() shouldBe Some("a")
        l.flip()
        l.pop() shouldBe Some("d")
        l.flip()
        l.pop() shouldBe Some("b")
        l.flip()
        l.pop() shouldBe Some("c")
        l.pop() shouldBe None
      }
    }
  }

  "used as a queue" should {
    "honor isFlipped state when enqueue() is called" in {
      Some(FlipList[String]()) foreach { l =>
        l.enqueue("a")
        l.flip()
        l.enqueue("b")
        l.flip()
        l.enqueue("c")
        l.flip()
        l.enqueue("d")
        l.flip()
        l.enqueue("e")

        l.dequeue() shouldBe Some("d")
        l.dequeue() shouldBe Some("b")
        l.dequeue() shouldBe Some("a")
        l.dequeue() shouldBe Some("c")
        l.dequeue() shouldBe Some("e")
        l.dequeue() shouldBe None
      }
    }

    "honor isFlipped state when dequeue() is called" in {
      Some(FlipList[String]()) foreach { l =>
        l.enqueue("a")
        l.enqueue("b")
        l.enqueue("c")
        l.enqueue("d")
        l.enqueue("e")

        l.dequeue() shouldBe Some("a")
        l.flip()
        l.dequeue() shouldBe Some("e")
        l.flip()
        l.dequeue() shouldBe Some("b")
        l.flip()
        l.dequeue() shouldBe Some("d")
        l.flip()
        l.dequeue() shouldBe Some("c")
        l.dequeue() shouldBe None
      }
    }
  }
}
