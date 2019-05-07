package com.septimalmind.server.services.auth

import java.util.UUID

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.septimalmind.server.externals.TokenService
import com.septimalmind.server.idl.RequestContext
import com.septimalmind.server.idl.RequestContext.{AdminRequest, ClientRequest}
import com.septimalmind.server.persistence.{UserRepo, UserSessionRepo}
import com.septimalmind.services.auth._
import com.septimalmind.services.shared.{DomainFailure, _}
import com.septimalmind.services.users.{ProtectedProfile, PublicProfile, UserId, UserProfile}

import scala.language.higherKinds

class LoginService[F[+ _, + _] : BIO]
(
  logger: IzLogger,
  tokenService: TokenService[F],
  userRepo: UserRepo[F],
  sessionRepo: UserSessionRepo[F]
) extends LoginServiceServer[F, RequestContext] {

  private val bio: BIO[F] = implicitly

  import bio._

  override def login
  (
    ctx: RequestContext,
    creds: Credentials
  ): F[DomainFailure, LoginResponse] = {

    logger.info(s"start processing ${creds -> "tokens"}")

    creds match {
      case Credentials.EmailPassword(emailPw) =>
        for {
          userId <- userRepo.findByEmail(emailPw.email)
          token <- generateToken(userId, ctx.networkContext.ip)
        } yield AccessResponse(token)
      case Credentials.PhonePassword(_) =>
        //TODO: implement
        throw new IllegalArgumentException("Not implemented")
    }
  }

  override def signUp
  (
    ctx: RequestContext,
    request: SignUpRequest
  ): F[DomainFailure, AccessResponse] = {

    ctx match {
      case AdminRequest(companyId, networkContext) =>
        val userId = UserId(UUID.randomUUID(), companyId.value)
        val user = UserProfile.apply(request.to[PublicProfile], request.to[ProtectedProfile], userId)
        userRepo.registerUser(user)
        generateToken(userId, networkContext.ip).map(AccessResponse(_))
      case _ =>
        fail(DomainFailure(DomainFailureCode.AccessDenied, Some("method allowed only for administrators")))
    }
  }

  def completeLogin(ctx: RequestContext, token: String): F[DomainFailure, AccessResponse] = {
    ???
  }

  override def logout
  (
    ctx: RequestContext
  ): F[DomainFailure, SuccessResponse] = {

    ctx match {
      case ClientRequest(userId, networkContext) =>
        for {
          _ <- checkoutSession(userId, networkContext.ip)
          _ <- sessionRepo.remove(userId, networkContext.ip)
        } yield SuccessResponse(None)
      case _ =>
        point(SuccessResponse(None))
    }
  }

  protected def generateToken(userId: UserId, ip: String): F[DomainFailure, String] = {
    for {
      _ <- point(logger.apply("user" -> userId, "ip" -> ip).info("generating access token"))
      token <- tokenService.generateToken(userId)
      _ <- sessionRepo.register(userId, ip, token)
    } yield token
  }

  private def checkoutSession(userId: UserId, ip: String): F[DomainFailure, Unit] = {
    sessionRepo.fetchSessionId(userId, ip).void.leftMap {
      case e@DomainFailure(DomainFailureCode.EntityNotFound, msg) =>
        logger.apply("user" -> userId, "ip" -> ip).info("can't find pair. reject")
        e.copy(code = DomainFailureCode.AccessDenied)
      case other =>
        other
    }
  }

}
