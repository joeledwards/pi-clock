package com.buzuli.collections

/**

// Minimal

 class FlipListNode[T](val value: T, l: FlipList[T]) {
   def list: Option[FlipList[T]] = ???

   def left: Option[FlipListNode[T]] = ???
   def right: Option[FlipListNode[T]] = ???

   def addToLeft(value: T): FlipListNode[T] = ???
   def addToRight(value: T): FlipListNode[T] = ???

   def remove(): FlipList[T] = ???
 }

 class FlipList[T] {
   def flip(): FlipList[T] = ???

   def left: Option[FlipListNode[T]] = ???
   def right: Option[FlipListNode[T]] = ???

   def pushLeft(value: T): Option[FlipListNode[T]] = ???
   def pushRight(value: T): Option[FlipListNode[T]] = ???

   def popLeft(value: T): Option[T] = ???
   def popRight(value: T): Option[T] = ???

   def isEmpty: Boolean = ???
   def iterator: Iterator[T] = ???
 }

*/

object DecommissionedNodeException extends Exception("This node is has been removed from the list.")

class FlipListNode[T](val value: T, l: FlipList[T]) {
  private var a: Option[FlipListNode[T]] = None
  private var b: Option[FlipListNode[T]] = None
  private var _list: Option[FlipList[T]] = Some(l)

  private def notInList[V](): V = throw DecommissionedNodeException

  private def withList[V](action: FlipList[T] => V): V = {
    _list match {
      case None => notInList()
      case Some(l) => action(l)
    }
  }

  /**
   * @return if the node has not been removed, a [[Some]] containing the [[FlipList]] of which this node is a member, otherwise [[None]]
   */
  def list: Option[FlipList[T]] = _list

  def isInList: Boolean = list.nonEmpty

  def right: Option[FlipListNode[T]] = withList { l => if (l.aIsLeft) b else a }
  def left: Option[FlipListNode[T]] = withList { l => if (l.aIsLeft) a else b }

  def next: Option[FlipListNode[T]] = right
  def prev: Option[FlipListNode[T]] = left

  private def aSideAdd(value: T): FlipListNode[T] = withList { l =>
    val newNode = new FlipListNode(value, l)
    val priorA = a
    a = Some(newNode)
    newNode.a = priorA
    newNode.b = Some(this)
    priorA match {
      case None => l.a = Some(newNode)
      case Some(pa) => pa.b = Some(newNode)
    }
    newNode
  }

  private def bSideAdd(value: T): FlipListNode[T] = withList { l =>
    val newNode = new FlipListNode(value, l)
    val priorB = b
    b = Some(newNode)
    newNode.b = priorB
    newNode.a = Some(this)
    priorB match {
      case None => l.b = Some(newNode)
      case Some(pb) => pb.a = Some(newNode)
    }
    newNode
  }

  /**
   * Add a value to the left side of this element in the list.
   *
   * @return the new [[FlipListNode]]
   */
  def addToLeft(value: T): FlipListNode[T] = withList { l =>
    if (l.aIsLeft) {
      aSideAdd(value)
    } else {
      bSideAdd(value)
    }
  }

  /**
   * Add a value to the right side of this element in the list.
   *
   * @return the new [[FlipListNode]]
   */
  def addToRight(value: T): FlipListNode[T] = withList { l =>
    if (l.aIsLeft) {
      bSideAdd(value)
    } else {
      aSideAdd(value)
    }
  }

  /**
   * Removes this node from the [[FlipList]].
   * Any subsequent attempt to do anything with the node other than access
   * [[value]] will result a [[DecommissionedNodeException]].
   *
   * @return the [[FlipList]]
   */
  def remove(): FlipList[T] = withList { l =>
    a match {
      case None => l.a = b
      case Some(pa) => pa.b = b
    }

    b match {
      case None => l.b = a
      case Some(pb) => pb.a = a
    }

    a = None
    b = None
    _list = None

    l
  }
}

class FlipList[T] {
  private var _aIsLeft: Boolean = true
  private[collections] def aIsLeft: Boolean = _aIsLeft

  private[collections] var a: Option[FlipListNode[T]] = None
  private[collections] var b: Option[FlipListNode[T]] = None

  /**
   * Reverses the list in constant time, and returns the list.
   *
   * @return the [[FlipList]]
   */
  def flip(): FlipList[T] = {
    _aIsLeft = !_aIsLeft
    this
  }

  /**
   * @return true if the list has been flipped from its original order
   */
  def isFlipped: Boolean = !aIsLeft

  /**
   * @return true if the list is empty
   */
  def isEmpty: Boolean = a.isEmpty || b.isEmpty

  /**
   * @return the left-most node of the list if it exists
   */
  def left: Option[FlipListNode[T]] = if (aIsLeft) a else b

  /**
   * @return the right-most node of the list if it exists
   */
  def right: Option[FlipListNode[T]] = if (aIsLeft) b else a

  /**
   * An alias to [[left]].
   */
  def head: Option[FlipListNode[T]] = left

  /**
   * An alias to [[right]].
   */
  def tail: Option[FlipListNode[T]] = right

  /**
   * Add the value to the left side of the list.
   *
   * @param value the value to add
   *
   * @return the new [[FlipListNode]]
   */
  def pushLeft(value: T): FlipListNode[T] = {
    (aIsLeft, a, b) match {
      case (true, Some(aNode), _) => aNode.addToLeft(value)
      case (false, _, Some(bNode)) => bNode.addToLeft(value)
      case _ => {
        val newNode = new FlipListNode(value, this)
        a = Some(newNode)
        b = Some(newNode)
        newNode
      }
    }
  }

  /**
   * Add the value to the right side of the list.
   *
   * @param value the value to add
   *
   * @return the new [[FlipListNode]]
   */
  def pushRight(value: T): FlipListNode[T] = {
    (aIsLeft, a, b) match {
      case (true, _, Some(bNode)) => bNode.addToRight(value)
      case (false, Some(aNode), _) => aNode.addToRight(value)
      case _ => {
        val newNode = new FlipListNode(value, this)
        a = Some(newNode)
        b = Some(newNode)
        newNode
      }
    }
  }

  /**
   * Remove and return the left-most value if the list was non-empty.
   *
   * @return a [[Some]] containing the value if the list was not empty, otherwise [[None]]
   */
  def popLeft(): Option[T] = (aIsLeft, a, b) match {
    case (true, Some(aNode), _) => {
      aNode.remove()
      Some(aNode.value)
    }
    case (false, _, Some(bNode)) => {
      bNode.remove()
      Some(bNode.value)
    }
    case _ => None
  }

  /**
   * Remove and return the right-most value if the list is non-empty.
   *
   * @return a [[Some]] containing the value if the list was not empty, otherwise [[None]]
   */
  def popRight(): Option[T] = (aIsLeft, a, b) match {
    case (true, _, Some(bNode)) => {
      bNode.remove()
      Some(bNode.value)
    }
    case (false, Some(aNode), _) => {
      aNode.remove()
      Some(aNode.value)
    }
    case _ => None
  }

  // stack methods
  def push(value: T): FlipListNode[T] = pushLeft(value)
  def pop(): Option[T] = popLeft()

  // queue methods
  def enqueue(value: T): FlipListNode[T] = pushRight(value)
  def dequeue(): Option[T] = popLeft()

  /**
   * @return An [[Iterator]] over the values in the list.
   *         Traversal is always left to right, so calling [[flip()]] will
   *         result in the list being traversed in the other direction.
   *         If [[flip()]] is called while traversing, the traversal order (not direction)
   *         will change on the fly, resulting in the same values being visited again.
   *         You can visualize what happens by thinking of the list as pivoting 180 degrees
   *         around the current node in the iterator when [[flip()]] is called.
   */
  def iterator: Iterator[T] = new Iterator[T] {
    private var node: Option[FlipListNode[T]] = left

    override def hasNext: Boolean = node.nonEmpty
    override def next(): T = node match {
      case None => throw new NoSuchElementException
      case Some(n) => {
        node = n.next
        n.value
      }
    }
  }

  /**
   * Invokes observer at every step of an [[Iterator]] created by calling [[iterator]].
   * Subject to the traversal behavior of [[iterator]].
   *
   * @param observer an observer function
   */
  def foreach(observer: T => Unit): Unit = iterator.foreach(observer)
}

object FlipList {
  /**
   * Creates an empty [[FlipList]].
   */
  def apply[T](): FlipList[T] = new FlipList

  /**
   * Creates a [[FlipList]] seeded with the values in the order supplied.
   */
  def apply[T](values: T*): FlipList[T] = {
    val list = new FlipList[T]
    values.foreach(v => list.pushRight(v))
    list
  }
}
