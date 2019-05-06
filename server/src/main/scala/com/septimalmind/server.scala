import java.util.UUID

import cats.data.OptionT
import cats.effect._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s._
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{ContextExtender, IRTClientMultiplexor, IRTServerMultiplexor, _}
import com.septimalmind.server.idl.{NetworkContext, RequestContext}
import com.septimalmind.server.idl.RequestContext.{AdminRequest, ClientRequest, GuestContext}
import com.septimalmind.server.services.auth.LoginService
import com.septimalmind.server.services.users.ProfileService
import com.septimalmind.services.auth.LoginServiceWrappedServer
import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.users.{UserId, UserProfileServiceWrappedServer}
import com.septimalmind.shared.RuntimeContext
import org.http4s.{AuthScheme, Credentials, Request}
import org.http4s.headers.Authorization
import scalaz.zio.interop.Task
import cats.data.Kleisli
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scalaz.zio.IO
import scalaz.zio.interop.catz._

import scala.util.Try

object server extends App with RuntimeContext {

  lazy val loginAPI = new LoginServiceWrappedServer[IO, RequestContext](new LoginService[IO])

  val adminAPI = new UserProfileServiceWrappedServer[IO, AdminRequest](new ProfileService[IO]).contramap[RequestContext] {
    case i: AdminRequest => i
    case _ => throw new IllegalArgumentException("REJECTED. unknown request context")
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

  def setupIDLRuntime(services: Set[IRTWrappedService[IO, RequestContext]], clients: Set[IRTWrappedClient], logger: IzLogger)(implicit bio: BIORunner[IO], timer: Timer[IO[Throwable, ?]]) = {

    val clientMultiplexor: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)

    val serverMultiplexor = new IRTServerMultiplexor[IO, RequestContext, RequestContext](services, ContextExtender.id)

    val (listeners, wsContextProvider, wsSessionStorage) = setupWsContext(rt, logger, clientMultiplexor)

    val authUser: Kleisli[OptionT[IO[Throwable, ?], ?], Request[IO[Throwable, ?]], RequestContext] =
      Kleisli {
        request: Request[IO[Throwable, ?]] =>
            OptionT.fromOption(prepareRequest(request))
      }

    val server = new HttpServer[rt.type](rt.self, serverMultiplexor, clientMultiplexor, AuthMiddleware(authUser), wsContextProvider, wsSessionStorage, listeners.toSeq, logger, printer)

    server.service
  }

  private def prepareRequest(request: Request[IO[Throwable, ?]]) : Option[RequestContext] = {
    lazy val networkContext = NetworkContext(request.remoteAddr.getOrElse("0.0.0.0"))
    import org.http4s.syntax.string._
    val privateScheme = "Api-Key".ci
    request.headers.find(_.name == "Authorization".ci).map(_.value.split(" ").toList match {
      case "Bearer" :: token :: Nil =>
        parseAsClient(networkContext, token)
      case scheme :: token :: Nil if scheme.ci == privateScheme =>
        parseAsAdmin(networkContext, token)
      case _ =>
        Some(GuestContext(networkContext))
    }).getOrElse(Some(GuestContext(networkContext)))
  }

  private def parseAsAdmin(networkContext: NetworkContext, token: String) : Option[AdminRequest] = {
    token.split("::").toList match {
      case "secret" :: id :: Nil =>
        Try(CompanyId.parse(id)).map(AdminRequest(_, networkContext)).toOption
      case _ =>
        None
    }
  }

  private def parseAsClient(networkContext: NetworkContext, token: String) : Option[ClientRequest] = {
    Try(UserId.parse(token)).map(ClientRequest(_, networkContext)).toOption
  }

  private def setupWsContext(rt: Http4sRuntime[IO, RequestContext, RequestContext, String, Unit, Unit], logger: IzLogger, codec: IRTClientMultiplexor[IO]) = {
    val listeners: Set[WsSessionListener[String]] = Set.empty
    val wsContextProvider: WsContextProvider[IO, RequestContext, String] = new IdContextProvider[rt.type](rt.self)
    val wsSessionStorage: WsSessionsStorage[IO, String, RequestContext] = new WsSessionsStorageImpl[rt.type](rt.self, logger, codec)
    (listeners, wsContextProvider, wsSessionStorage)
  }
}
