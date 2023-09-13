package com.buzuli.util

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * This class represents a resources which is lazily initialized.
 *
 * @param initializer a function which attempts to initialize the value asynchronously
 * @param resetOnFailure if this is set to true, subsequent attempts to call get() will restart initialization
 */
class LazyResource[T](
  initializer: () => Future[T],
  resetOnFailure: Boolean = false
) {
  sealed trait ResourceState {
    def isDormant: Boolean = false
    def isInitializing: Boolean = false
    def isFailed: Boolean = false
    def isReady: Boolean = false
  }

  case object Dormant extends ResourceState { override def isDormant = true }
  case class Initializing(future: Future[T]) extends ResourceState { override def isInitializing = true}
  case class Failed(cause: Throwable) extends ResourceState { override def isFailed = true }
  case class Ready(value: T) extends ResourceState { override def isReady = true }

  private var _attempts: Int = 0
  private var _state: ResourceState = Dormant

  private def doInit()(implicit ec: ExecutionContext): Future[T] = {
    _attempts += 1
    val future = Future.unit flatMap { _ =>
      initializer()
    } andThen {
      case Success(value) => _state = Ready(value)
      case Failure(cause) => _state = Failed(cause)
    }
    _state = Initializing(future)
    future
  }

  private def init()(implicit ec: ExecutionContext): Future[T] = this.synchronized {
    _state match {
      case Dormant => doInit()
      case Initializing(future) => future
      case Ready(value) => Future.successful(value)
      case Failed(cause) => {
        if (resetOnFailure) {
          doInit()
        } else {
          Future.failed(cause)
        }
      }
    }
  }

  def get(implicit ec: ExecutionContext): Future[T] = _state match {
    case Dormant => init()
    case Initializing(future) => future
    case Ready(value) => Future.successful(value)
    case Failed(_) => init()
  }

  def attempts: Int = _attempts

  def dormant: Boolean = _state.isDormant
  def initializing: Boolean = _state.isInitializing
  def failed: Boolean = _state.isFailed
  def ready: Boolean = _state.isReady
}

object LazyResource {
  def apply[T](initializer: () => Future[T], resetOnFailure: Boolean = false): LazyResource[T] = {
    new LazyResource(initializer, resetOnFailure)
  }
}
