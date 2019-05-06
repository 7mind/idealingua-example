package com.septimalmind.server.externals

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.septimalmind.services.shared.{DomainFailure, DomainFailureCode}
import com.septimalmind.services.users.UserId

class TokenService[F[+_, +_]: BIO] {
  def generateToken(userId: UserId) : F[DomainFailure, String] = {
    BIO[F].sync(userId.toString())
  }
  def parseToken(token: String) : F[DomainFailure, UserId] = {
    BIO[F].syncThrowable(UserId.parse(token))
      .leftMap(thr => DomainFailure(DomainFailureCode.AssertionFailed, Some(s"can't parse. reason: ${thr.getMessage}")))
  }
}
