package com.septimamind.test

import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.septimalmind.server.externals.TokenService
import com.septimalmind.server.idl.RequestContext.{AdminRequest, ClientRequest, GuestRequest}
import com.septimalmind.server.idl.{NetworkContext, RequestContext}
import com.septimalmind.server.persistence.{UserRepo, UserSessionRepo}
import com.septimalmind.server.services.auth.LoginService
import com.septimalmind.services.auth.LoginServiceServer
import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.shared.DomainFailure
import com.septimalmind.services.users.UserId
import com.septimalmind.shared.RuntimeContext
import org.scalatest.WordSpec
import zio.IO

class LoginRequestSpec extends WordSpec with RuntimeContext {

  def guest = GuestRequest(NetworkContext("0.0.0.0"))

  def admin(companyId: CompanyId) = AdminRequest(companyId, NetworkContext("0.0.0.0"))

  def client(userId: UserId) = ClientRequest(userId, NetworkContext("0.0.0.0"))

  val tokenService: TokenService[IO] = new TokenService[IO]

  private val userRepo = new UserRepo[IO]
  private val sessionRepo = new UserSessionRepo[IO]

  val loginService: LoginServiceServer[IO, RequestContext] = new LoginService[IO](logger, tokenService, userRepo, sessionRepo)

  private def scopeIO[T](t : IO[DomainFailure, Unit])(implicit bio: BIORunner[IO]) : Unit = {
    val eff = t.leftMap(failure => fail(s"Got failure. ${failure.code.getClass.getName} ${failure.msg.getOrElse("")}"))
    bio.unsafeRun(eff)
  }
}









