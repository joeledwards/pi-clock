package com.buzuli.util

import java.util.Optional

import scala.jdk.CollectionConverters._

object Types {
  object option {
    def zip[A, B](opt_a: Option[A], opt_b: Option[B]): Option[(A, B)] = {
      (opt_a, opt_b) match {
        case (Some(a), Some(b)) => Some((a, b))
        case _ => None
      }
    }

    def unzip[A, B](opt: Option[(A, B)]): (Option[A], Option[B]) = {
      opt match {
        case None => (None, None)
        case Some((a, b)) => (Some(a), Some(b))
      }
    }

    def fromJava[T](opt: Optional[T]): Option[T] = opt.map[Option[T]](v => Some(v)).orElse(None)

    def toJava[T](opt: Option[T]): Optional[T] = opt match {
      case None => Optional.empty()
      case Some(v) => Optional.of(v)
    }
  }

  object list {
    def fromJava[T](l: java.util.List[T]): List[T] = l.asScala.toList

    def toJava[T](l: List[T]): java.util.List[T] = l.asJava
  }

  object map {
    def fromJava[K,V](m: java.util.Map[K,V]): Map[K,V] = m.asScala.toMap

    def toJava[K,V](m: Map[K,V]): java.util.Map[K,V] = m.asJava
  }

  object set {
    def fromJava[T](s: java.util.Set[T]): Set[T] = s.asScala.toSet

    def toJava[T](s: Set[T]): java.util.Set[T] = s.asJava
  }
}
