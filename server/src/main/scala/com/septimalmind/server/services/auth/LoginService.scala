package com.septimalmind.server.services.auth

import java.util.UUID

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.septimalmind.server.externals.TokenService
import com.septimalmind.server.idl.RequestContext
import com.septimalmind.server.idl.RequestContext.{ AdminRequest, ClientRequest }
import com.septimalmind.server.persistence.{ UserRepo, UserSessionRepo }
import com.septimalmind.services.auth._
import com.septimalmind.services.shared._
import com.septimalmind.services.users._

import scala.language.higherKinds

class LoginService[F[+ _, + _]: BIO](
  logger: IzLogger,
  tokenService: TokenService[F],
  userRepo: UserRepo[F],
  sessionRepo: UserSessionRepo[F]
) extends LoginServiceServer[F, RequestContext] {

  private val bio: BIO[F] = implicitly

  import bio._

  override def login(
    ctx: RequestContext,
    creds: Credentials
  ): F[DomainFailure, LoginResponse] = {

    logger.info(s"start processing ${creds -> "tokens"}")

    for {
      (user, pw) <- creds match {
                     case Credentials.EmailPassword(emailPw) =>
                       userRepo.findByEmail(emailPw.email).map(_ -> emailPw.password)
                     case Credentials.PhonePassword(phonePw) =>
                       userRepo.findByPhone(phonePw.phone).map(_ -> phonePw.password)
                   }
      id     = user.id
      ip     = ctx.networkContext.ip
      _      <- userRepo.checkPw(id, pw)
      format <- userRepo.retrieveUserAuthFormat(id)
      out <- {
        import LoginResponse._
        format match {
          case TwoFactorAuthFormat.None =>
            generateToken(id, ip).map(LoginResponse.intoAccessResponse)
          case enabled =>
            twoFactorAuthenticate(user.id, enabled, ip).map(LoginResponse.intoPendingConfirmation)
        }
      }
    } yield out
  }

  override def signUp(
    ctx: RequestContext,
    request: SignUpRequest
  ): F[DomainFailure, AccessResponse] =
    ctx match {
      case AdminRequest(companyId, networkContext) =>
        val userId = UserId(UUID.randomUUID(), companyId.value)
        val user   = UserProfile.apply(request.to[PublicProfile], request.to[ProtectedProfile], userId)
        for {
          _     <- userRepo.registerUser(user)
          _     <- userRepo.upsertAuthFormat(userId, request.authFormat)
          token <- generateToken(userId, networkContext.ip)
        } yield AccessResponse(token)
      case _ =>
        fail(DomainFailure(DomainFailureCode.AccessDenied, Some("method allowed only for administrators")))
    }

  def completeLogin(ctx: RequestContext, token: String): F[DomainFailure, AccessResponse] = {
    
  }
    

  override def logout(
    ctx: RequestContext
  ): F[DomainFailure, SuccessResponse] =
    ctx match {
      case ClientRequest(userId, networkContext) =>
        for {
          _ <- checkoutSession(userId, networkContext.ip)
          _ <- sessionRepo.remove(userId, networkContext.ip)
        } yield SuccessResponse(None)
      case _ =>
        point(SuccessResponse(None))
    }

  protected def twoFactorAuthenticate(
    userId: UserId,
    format: TwoFactorAuthFormat,
    ip: String
  ): F[DomainFailure, PendingConfirmation] = ???

  protected def generateToken(userId: UserId, ip: String): F[DomainFailure, AccessResponse] =
    for {
      _     <- point(logger.apply("user" -> userId, "ip" -> ip).info("generating access token"))
      token <- tokenService.generateToken(userId)
      _     <- sessionRepo.register(userId, ip, token)
    } yield AccessResponse(token)

  private def checkoutSession(userId: UserId, ip: String): F[DomainFailure, Unit] =
    sessionRepo.fetchSessionId(userId, ip).void.leftMap {
      case e @ DomainFailure(DomainFailureCode.EntityNotFound, msg) =>
        logger.apply("user" -> userId, "ip" -> ip).info("can't find pair. reject")
        e.copy(code = DomainFailureCode.AccessDenied)
      case other =>
        other
    }

}
