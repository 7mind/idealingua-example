package com.septimalmind

import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.ClientDispatcher
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, IRTWrappedClient}
import com.septimalmind.services.auth.LoginServiceWrappedClient
import com.septimalmind.services.users.UserProfileServiceWrappedClient
import com.septimalmind.shared.RuntimeContext
import org.http4s.{Header, Request, Uri}
import zio.{IO, ZIO}
import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.auth.SignUpRequest
import com.septimalmind.services.auth.EmailPassword
import com.septimalmind.services.shared.DomainFailureCode

object Client extends zio.App with RuntimeContext {
  private val printer = io.circe.Printer.spaces2
  private val uri = Uri.fromString("http://localhost:8080/v2").right.get
  private val clients: Set[IRTWrappedClient] = Set(LoginServiceWrappedClient, UserProfileServiceWrappedClient)
  private val clientMultiplexor: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)

  override def run(args: List[String]): ZIO[Server.Environment, Nothing, Int] = {
    val program = for {
      runtime <- http4sRuntime(timer)
      dispatcher = adminDispatcher(CompanyId(1L))(runtime)
      adminClient = new LoginServiceWrappedClient[IO](dispatcher)
      signupToken <- adminClient.signUp(SignUpRequest("test-user", "email@foo.com", "+3801234567", "qwerty12345"))
      _ <- adminClient
        .login(EmailPassword("wrong-email", "qwerty12345"))
        .flip
        .map(failure => assert(failure.code == DomainFailureCode.EntityNotFound))
      loginResponse <- adminClient.login(EmailPassword("email@foo.com", "qwerty12345"))
      _ = assert(loginResponse.accessToken == signupToken.accessToken)
    } yield ()

    program.foldM(err => zio.console.putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), _ => ZIO.succeed(0))
  }

  private def adminDispatcher(companyId: CompanyId)(rt: Runtime): ClientDispatcher[rt.DECL] =
    new ClientDispatcher[rt.DECL](rt.self, logger, printer, uri, clientMultiplexor) {
      override protected def transformRequest(request: Request[c.MonoIO]): Request[c.MonoIO] = {
        val header = Header("Authorization", s"Api-Key secret::${companyId.toString()}")
        request.withHeaders(header)
      }
    }
}
