import java.util.UUID

import cats.data.OptionT
import cats.effect._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{ContextExtender, IRTClientMultiplexor, IRTServerMultiplexor, _}
import com.septimalmind.server.idl.{NetworkContext, RequestContext}
import com.septimalmind.server.idl.RequestContext.AdminRequest
import com.septimalmind.server.services.auth.LoginService
import com.septimalmind.server.services.users.ProfileService
import com.septimalmind.services.auth.LoginServiceWrappedServer
import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.users.{UserId, UserProfileServiceWrappedServer}
import com.septimalmind.shared.RuntimeContext
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

object server extends App with RuntimeContext {

  override def main(args: Array[String]) = {

    implicit val bio = setupBio(logger)

    val loginAPI = new LoginServiceWrappedServer[IO, RequestContext](new LoginService[IO])


    val adminAPI = new UserProfileServiceWrappedServer[IO, AdminRequest](new ProfileService[IO]).contramap[RequestContext] {
      case i: AdminRequest => i
      case other => throw new IllegalArgumentException("REJECTED. unknown request context")
    }

    val idlRouter = setupIDLRuntime(Set(loginAPI, adminAPI): Set[IRTWrappedService[IO, RequestContext]], Set.empty, logger)

    val allRoutes = Router(List(idlRouter).map {
      svc => "/" -> svc
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

  def setupIDLRuntime(services: Set[IRTWrappedService[IO, RequestContext]], clients: Set[IRTWrappedClient], logger: IzLogger)(implicit bio: BIORunner[IO], timer: Timer[IO[Throwable, ?]]) = {
    val rt = new Http4sRuntime[IO, RequestContext, RequestContext, String, Unit, Unit](scala.concurrent.ExecutionContext.global)

    val authUser: Kleisli[OptionT[IO[Throwable, ?], ?], Request[IO[Throwable, ?]], RequestContext] =
      Kleisli {
        request: Request[IO[Throwable, ?]] =>
          val context = RequestContext.AdminRequest(UserId(UUID.randomUUID(), UUID.randomUUID()), NetworkContext(request.remoteAddr.getOrElse("0.0.0.0")))
          OptionT.liftF(Task(context))
      }

    val listeners: Set[WsSessionListener[String]] = Set.empty

    val printer: io.circe.Printer = io.circe.Printer.spaces2

    val codec: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)

    val wsContextProvider: WsContextProvider[IO, RequestContext, String] = new IdContextProvider[rt.type](rt.self)

    val wsSessionStorage: WsSessionsStorage[IO, String, RequestContext] = new WsSessionsStorageImpl[rt.type](rt.self, logger, codec)

    val multiplexor = new IRTServerMultiplexor[IO, RequestContext, RequestContext](services, ContextExtender.id)

    val server = new HttpServer[rt.type](rt.self, multiplexor, codec, AuthMiddleware(authUser), wsContextProvider, wsSessionStorage, listeners.toSeq, logger, printer) {
    }

    server.service
  }
}
