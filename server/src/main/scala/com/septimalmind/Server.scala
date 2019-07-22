package com.septimalmind

import com.github.pshirshov.izumi.idealingua.runtime.rpc.{ContextExtender, IRTClientMultiplexor, IRTServerMultiplexor, _}
import cats.data.{Kleisli, OptionT}
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s._
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.septimalmind.server.externals.TokenService
import com.septimalmind.server.idl.RequestContext.{AdminRequest, ClientRequest, GuestRequest}
import com.septimalmind.server.idl.{NetworkContext, RequestContext}
import com.septimalmind.server.persistence.{UserRepo, UserSessionRepo}
import com.septimalmind.server.services.auth.LoginService
import com.septimalmind.server.services.users.ProfileService
import com.septimalmind.services.auth.LoginServiceWrappedServer
import com.septimalmind.services.users.UserProfileServiceWrappedServer
import com.septimalmind.shared.RuntimeContext
import org.http4s.Request
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.syntax.kleisli._
import zio.{IO, ZIO}
import zio.interop.catz._
import zio.console.putStrLn
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import scala.util.Try

object Server extends zio.App with RuntimeContext {
  private val port = 8080

  override def run(args: List[String]): ZIO[Server.Environment, Nothing, Int] = {
    import zio.interop.catz._

    val program = for {
      services <- ZIO.fromTry(Try(createServices()))
      _ <- putStrLn(s"list of IDL:\n${services.map(_.serviceId.value).mkString("\n", "\n", "\n") -> "APIs"}")
      router = createHttpRouter(services)
      server <- ZIO.runtime.flatMap { implicit rt =>
        BlazeServerBuilder[IO[Throwable, ?]]
          .bindHttp(port, "0.0.0.0")
          .withExecutionContext(scala.concurrent.ExecutionContext.global)
          .withHttpApp(router)
          .serve
          .compile
          .drain
      }
    } yield server

    program.foldM(err => zio.console.putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), _ => ZIO.succeed(0))
  }

  private def createServices() = {
    val tokenService = new TokenService[IO]
    val userRepo = new UserRepo[IO]
    val sessionRepo = new UserSessionRepo[IO]

    val loginAPI = new LoginServiceWrappedServer[IO, RequestContext](new LoginService[IO](logger, tokenService, userRepo, sessionRepo))

    val adminAPI: IRTWrappedService[IO, RequestContext] = new UserProfileServiceWrappedServer[IO, AdminRequest](new ProfileService[IO]).contramap[RequestContext] {
      case i: AdminRequest => i
      case _ => throw new IllegalArgumentException("REJECTED. unknown request context")
    }

    Set(loginAPI, adminAPI)
  }

  private def createHttpRouter(services: Set[IRTWrappedService[IO, RequestContext]]) = {
    val idlRouter = "/v2/" -> setupIDLRuntime(
      services,
      Set.empty,
      logger
    )
    val heartbeatRoute = "/v1/" -> HttpRoutes.of {
      case GET -> Root / "heartbeat" =>
        Sync[IO[Throwable, ?]]
          .pure(Response(Status.Ok, headers = Headers.of(Header("Response-Issuer", "PUT_SERVICE_NAME_HERE"))))
    }

    Router(List(idlRouter, heartbeatRoute): _*).orNotFound
  }

  private def setupIDLRuntime(services: Set[IRTWrappedService[IO, RequestContext]],
                      clients: Set[IRTWrappedClient], logger: IzLogger)
                     (implicit bio: BIORunner[IO], timer: Timer[IO[Throwable, ?]]): HttpRoutes[IO[Throwable, ?]] = {
    def prepareRequest(request: Request[IO[Throwable, ?]]) : Option[RequestContext] = {
      lazy val networkContext = NetworkContext(request.remoteAddr.getOrElse("0.0.0.0"))
      import org.http4s.syntax.string._
      val privateScheme = "Api-Key".ci
      request.headers.find(_.name == "Authorization".ci).map(_.value.split(" ").toList match {
        case "Bearer" :: token :: Nil =>
          ClientRequest.fromHeader(token, networkContext)
        case scheme :: token :: Nil if scheme.ci == privateScheme =>
          AdminRequest.fromHeader(token, networkContext)
        case _ =>
          Some(GuestRequest(networkContext))
      }).getOrElse(Some(GuestRequest(networkContext)))
    }

    def setupWsContext(rt: Http4sRuntime[IO, RequestContext, RequestContext, String, Unit, Unit], logger: IzLogger, codec: IRTClientMultiplexor[IO]) = {
      val listeners: Set[WsSessionListener[String]] = Set.empty
      val wsContextProvider: WsContextProvider[IO, RequestContext, String] = new IdContextProvider[rt.type](rt.self)
      val wsSessionStorage: WsSessionsStorage[IO, String, RequestContext] = new WsSessionsStorageImpl[rt.type](rt.self, logger, codec)
      (listeners, wsContextProvider, wsSessionStorage)
    }

    val clientMultiplexor: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)
    val serverMultiplexor = new IRTServerMultiplexor[IO, RequestContext, RequestContext](services, ContextExtender.id)

    val (listeners, wsContextProvider, wsSessionStorage) = setupWsContext(rt, logger, clientMultiplexor)

    val authUser: Kleisli[OptionT[IO[Throwable, ?], ?], Request[IO[Throwable, ?]], RequestContext] =
      Kleisli {
        request: Request[IO[Throwable, ?]] =>
          OptionT.fromOption(prepareRequest(request))
      }

    val server = new HttpServer[rt.type](
      rt.self,
      serverMultiplexor,
      clientMultiplexor,
      AuthMiddleware(authUser),
      wsContextProvider,
      wsSessionStorage,
      listeners.toSeq,
      logger,
      printer
    ) {}

    server.service
  }
}
