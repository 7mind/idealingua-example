import java.util.concurrent.{Executors, ThreadPoolExecutor}

import cats.data.OptionT
import cats.effect._
import com.github.pshirshov.izumi.functional.bio.BIOExit
import com.github.pshirshov.izumi.functional.bio.BIORunner.DefaultHandler
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{ContextExtender, IRTClientMultiplexor, IRTServerMultiplexor}
import com.septimalmind.server.idl.RequestContext
import com.septimalmind.server.services.auth.LoginService
import com.septimalmind.services.auth.LoginServiceWrappedServer
import org.http4s._
import scalaz.zio.internal.NamedThreadFactory
import scalaz.zio.interop.Task

import scala.concurrent.duration.FiniteDuration
//import org.http4s.dsl.io
import cats.data.Kleisli
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.Request
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scalaz.zio.IO
import scalaz.zio.interop.catz._

object server extends App {

  override def main(args: Array[String]) = {

    val logger: IzLogger = IzLogger.DebugLogger

    val cpuPool: ThreadPoolExecutor = Executors.newFixedThreadPool(8).asInstanceOf[ThreadPoolExecutor]
    val ioPool: ThreadPoolExecutor = Executors.newFixedThreadPool(8).asInstanceOf[ThreadPoolExecutor]
    val timerPool = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

    implicit val bio = BIORunner.createZIO(cpuPool, ioPool, DefaultHandler.Custom {
      case BIOExit.Error(error: Throwable) =>
        val stackTrace = error.getStackTrace
        IO.sync(logger.warn(s"Fiber terminated with unhandled Throwable $error $stackTrace"))
      case BIOExit.Error(error) =>
        IO.sync(logger.warn(s"Fiber terminated with unhandled $error"))
      case BIOExit.Termination(_, (_: InterruptedException) :: _) =>
        IO.unit // don't log interrupts
      case BIOExit.Termination(exception, _) =>
        IO.sync(logger.warn(s"Fiber terminated erroneously with unhandled defect $exception"))
    }, 1024, timerPool)


    val svc = new LoginServiceWrappedServer[IO, RequestContext](new LoginService[IO])

    val codec: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](Set.empty)

    val multiplexor = new IRTServerMultiplexor[IO, RequestContext, RequestContext](Set(svc), ContextExtender.id)

    implicit lazy val timer: Timer[IO[Throwable, ?]] = new Timer[IO[Throwable, ?]] {
      val clock: Clock[IO[Throwable, ?]]  = Clock.create[IO[Throwable, ?]]
      override def sleep(duration: FiniteDuration): IO[Throwable, Unit] = IO.sleep(scalaz.zio.duration.Duration.fromScala(duration))
    }

    val rt = new Http4sRuntime[IO, RequestContext, RequestContext, String, Unit, Unit](scala.concurrent.ExecutionContext.global)

    val wsContextProvider: WsContextProvider[IO, RequestContext, String] = new IdContextProvider[rt.type](rt.self)

    val wsSessionStorage: WsSessionsStorage[IO, String, RequestContext] = new WsSessionsStorageImpl[rt.type](rt.self, logger, codec)

    val authUser: Kleisli[OptionT[rt.MonoIO, ?], Request[rt.MonoIO], RequestContext] =
      Kleisli {
        request: Request[rt.MonoIO] =>
          val context = RequestContext()
          //        val context = RequestContext(request.remoteAddr.getOrElse("0.0.0.0"), request.headers.get(Authorization).map(_.credentials))
          OptionT.liftF(Task(context))
      }


    val authenticator: AuthMiddleware[IO[Throwable, ?], RequestContext] = AuthMiddleware(authUser)

    val listeners: Set[WsSessionListener[String]] = Set.empty

    val printer: io.circe.Printer = io.circe.Printer.spaces2

    def httpServer() = new HttpServer[rt.type](rt.self, multiplexor, codec, authenticator, wsContextProvider, wsSessionStorage, listeners.toSeq, logger, printer) {
    }


    val allRoutes = Router(List(httpServer().service).map {
      case svc =>
        "/v2" -> svc
    }: _*).orNotFound

    val server = BlazeServerBuilder[IO[Throwable, ?]]
      .bindHttp(8080, "0.0.0.0")
      .withExecutionContext(scala.concurrent.ExecutionContext.global)
      .withHttpApp(allRoutes)
      .serve
      .compile
      .drain
    bio.unsafeRun(server)
  }


}
