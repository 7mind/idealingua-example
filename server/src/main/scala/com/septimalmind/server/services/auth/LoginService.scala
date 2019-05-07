package com.septimalmind.server.services.auth

import java.util.UUID

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.septimalmind.server.externals.TokenService
import com.septimalmind.server.idl.RequestContext
import com.septimalmind.server.idl.RequestContext.{AdminRequest, ClientRequest}
import com.septimalmind.server.persistence.{UserRepo, UserSessionRepo}
import com.septimalmind.services.auth.TwoFactorAuthFormat.{Email, SMS, TOTP}
import com.septimalmind.services.auth.{LoginResponse, _}
import com.septimalmind.services.shared._
import com.septimalmind.services.users._

import scala.language.higherKinds
import scala.util.Random

class LoginService[F[+ _, + _]: BIO](
  logger: IzLogger,
  tokenService: TokenService[F],
  userRepo: UserRepo[F],
  sessionRepo: UserSessionRepo[F],
  pendingConfirmation: PendingConfirmationRepo[F]
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

  def completeLogin(ctx: RequestContext, acuireId: UUID, token: String): F[DomainFailure, AccessResponse] = {
    for {
      (userId, expectedToken) <- pendingConfirmation.fetch(acuireId)
      _   = logger.info("")
      _  <- when(expectedToken == token)(fail(DomainFailure(DomainFailureCode.AssertionFailed, Some("wrong token"))))
      _  <- pendingConfirmation.release(acuireId)
      token <- generateToken(userId, ctx.networkContext.ip)
    } yield AccessResponse(token)
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
  ): F[DomainFailure, PendingConfirmation] = {
    val token = (Random.nextInt(999999) + 100000).toString
    for {
      _ <- (format : @unchecked) match {
        case SMS => viaSms(userId, token)
        case Email => viaEmail(userId, token)
        case TOTP => viaGoogle(userId, token)
      }
      lockId <- pendingConfirmation.acquire(userId, token)
    } yield PendingConfirmation(format, lockId, IzTime.utcNow.plusMinutes(30L))
  }

  private def viaGoogle(userId: UserId, token: String) : F[DomainFailure, Unit] = {
    Quirks.discard(userId, token)
    unit
  }
  private def viaSms(userId: UserId, token: String) : F[DomainFailure, Unit] = {
    Quirks.discard(userId, token)
    unit
  }
  private def viaEmail(userId: UserId, token: String) : F[DomainFailure, Unit] = {
    Quirks.discard(userId, token)
    unit
  }


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
