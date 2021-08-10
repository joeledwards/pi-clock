package com.buzuli.util

import com.buzuli.AsyncUnitSpec

import scala.concurrent.Future

class LazyResourceSpec extends AsyncUnitSpec {
  "LazyResource" when {
    "dormant" should {
      "contain no value" in {
        def initializer(): Future[String] = {
          Future.successful("ready")
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)
      }
    }

    "initializing" should {
      "report initializing until complete" in {
        def initializer(): Future[String] = {
          Async.delay(50) map { _ =>
            "ready"
          }
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)
        resource.get
        assert(resource.initializing)
      }
    }

    "ready" should {
      "report ready once complete" in {
        def initializer(): Future[String] = {
          Async.delay(50) map { _ =>
            "ready"
          }
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)
        resource.get
        assert(resource.initializing)
      }

      "resolve promise on success" in {
        def initializer(): Future[String] = {
          Async.delay(50) map { _ =>
            "ready"
          }
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)
        resource.get
        assert(resource.initializing)
      }
    }

    "failed" should {
      "report failed when initialization fails" in {
        def initializer(): Future[String] = {
          Async.delay(5) flatMap { _ =>
            Future.failed(new Exception("failure"))
          }
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)

        resource.get recover { _ =>
          ()
        } map { _ =>
          assert(resource.failed)
        }
      }

      "report failure after multiple get calls" in {
        var first = true
        def initializer(): Future[String] = {
          Async.delay(5) flatMap { _ =>
            if (first) {
              Future.failed(new Exception("If at first you don't succeed..."))
            } else {
              first = false
              Future.successful("try again")
            }
          }
        }

        val resource = LazyResource(initializer)
        assert(resource.dormant)

        Future.unit flatMap { _ =>
          resource.get
        } recover { _ =>
          ()
        } flatMap { _ =>
          assert(resource.failed)
          resource.get
        } recover { _ =>
          ()
        } map { _ =>
          assert(resource.failed)
        }
      }

      "report ready on init success after failure when configured with resetOnFailure=true" in {
        var count = 0
        def initializer(): Future[String] = {
          Async.delay(5) flatMap { _ =>
            if (count > 0) {
              Future.failed(new Exception("If at first you don't succeed..."))
            } else {
              count += 1
              Future.successful("try again")
            }
          }
        }

        val resource = LazyResource(initializer, resetOnFailure = true)
        assert(resource.dormant)

        Future.unit flatMap { _ =>
          resource.get
        } recover { _ =>
          ()
        } flatMap { _ =>
          assert(resource.failed)
          resource.get
        } recover { _ =>
          ()
        } map { _ =>
          assert(resource.ready)
        }
      }
    }
  }
}
