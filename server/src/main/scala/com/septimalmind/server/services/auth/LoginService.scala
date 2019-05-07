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
  ): F[DomainFailure, AccessResponse] = {

    logger.info(s"incomming login}")

    creds match {
      case Credentials.EmailPassword(emailPw) =>
        val ctxLog = logger.apply("email" -> emailPw.email)
        for {
          userId <- userRepo.findByEmail(emailPw.email)
          _  = logger.info("found email for user. generaing token")
          token <- generateToken(userId, ctx.networkContext.ip)
          _ = logger.info("granted access for user")
        } yield AccessResponse(token)
      case Credentials.PhonePassword(_) =>
        //TODO: implement
        ???
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
        val ctxLog = logForUser(logger, userId)
        ctxLog.info("creating new user")
        val user = UserProfile.apply(request.to[PublicProfile], request.to[ProtectedProfile], userId)
        for {
          _ <- userRepo.registerUser(user)
          _ = ctxLog.info("registered in DB. generating token")
          out <- generateToken(userId, networkContext.ip).map(AccessResponse(_))
        } yield out
        case _ =>
        fail(DomainFailure(DomainFailureCode.AccessDenied, Some("method allowed only for administrators")))
    }
  }

  override def logout
  (
    ctx: RequestContext
  ): F[DomainFailure, SuccessResponse] = {
    ctx match {
      case ClientRequest(userId, networkContext) =>
        val ctxLog = logForUser(logger, userId)
        ctxLog.info("logouting...")
        for {
          _ <- checkoutSession(userId, networkContext.ip)
          _  = ctxLog.info("removing session...")
          _ <- sessionRepo.remove(userId, networkContext.ip)
          _ <- point(ctxLog.info("logouting finised"))
        } yield SuccessResponse(None)
      case _ =>
        logger.info("discarding incomming request")
        point(SuccessResponse(None))
    }
  }

  protected def generateToken(userId: UserId, ip: String): F[DomainFailure, String] = {
    val ctxLog = logForUser(logger, userId)
    for {
      _ <- point(ctxLog.info("generating access token"))
      token <- tokenService.generateToken(userId)
      _ <- point(ctxLog.info("register new session"))
      _ <- sessionRepo.register(userId, ip, token)
    } yield token
  }

  private def checkoutSession(userId: UserId, ip: String): F[DomainFailure, Unit] = {
    val ctxLog = logForUser(logger, userId)
    ctxLog.info("verifying session")
    sessionRepo.fetchSessionId(userId, ip).void.leftMap {
      case e@DomainFailure(DomainFailureCode.EntityNotFound, msg) =>
        ctxLog.info("can't find pair. reject")
        e.copy(code = DomainFailureCode.AccessDenied)
      case other =>
        ctxLog.info("sesion verified")
        other
    }
  }

  private def logForUser(logger: IzLogger, userId: UserId) : IzLogger = {
   logger.apply("user-id" -> userId.uid, "company" -> userId.company)
  }

}
