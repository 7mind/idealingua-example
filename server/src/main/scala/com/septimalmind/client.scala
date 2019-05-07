package com.septimalmind

import java.util.UUID

import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.ClientDispatcher
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{ IRTClientMultiplexor, IRTWrappedClient }
import com.septimalmind.services.auth.LoginServiceWrappedClient
import com.septimalmind.services.users.UserProfileServiceWrappedClient
import com.septimalmind.shared.RuntimeContext
import org.http4s.{ Header, Request, Uri }
import scalaz.zio.IO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.septimalmind.services.users.UserId
import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.auth.SignUpRequest
import com.septimalmind.services.auth.EmailPassword
import com.septimalmind.services.shared.DomainFailureCode

object client extends App with RuntimeContext {

  val uri = Uri.fromString("http://localhost:8080/v2").right.get

  val clients: Set[IRTWrappedClient] = Set(LoginServiceWrappedClient, UserProfileServiceWrappedClient)

  val clientMultiplexor: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)

  def adminDispatcher(company: CompanyId) =
    new ClientDispatcher[rt.DECL](rt.self, logger, printer, uri, clientMultiplexor) {
      override protected def transformRequest(request: Request[c.MonoIO]): Request[c.MonoIO] = {
        val header = Header("Authorization", s"Api-Key secret::${company.toString()}")
        request.withHeaders(header)
      }
    }

  def clientDispatcher(token: String) =
    new ClientDispatcher[rt.DECL](rt.self, logger, printer, uri, clientMultiplexor) {
      override protected def transformRequest(request: Request[c.MonoIO]): Request[c.MonoIO] = {
        val header = Header("Authorization", s"Bearer $token")
        request.withHeaders(header)
      }
    }

  def loginClientForCompany(company: CompanyId) =
    new LoginServiceWrappedClient[IO](adminDispatcher(company))

  // basic flow

  val company     = CompanyId(1L)
  val adminClient = loginClientForCompany(company)

  val out = for {
    signupToken <- adminClient.signUp(SignUpRequest("test-user", "email@foo.com", "+3801234567", "qwerty12345"))
    _  = println(signupToken.accessToken)
    _ <- adminClient
          .login(EmailPassword("wrong-email", "qwerty12345"))
          .flip
          .map(failure => assert(failure.code == DomainFailureCode.EntityNotFound))
    loginResponse <- adminClient.login(EmailPassword("email@foo.com", "qwerty12345"))
    _ = assert(loginResponse.accessToken == signupToken.accessToken)
  } yield ()

  bio.unsafeRun(out)
}
