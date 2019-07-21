package com.septimalmind.server.persistence

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.septimalmind.services.shared.{DomainFailure, DomainFailureCode}
import com.septimalmind.services.users.UserId

import scala.language.higherKinds

class UserSessionRepo[F[+_, +_]: BIO] {

  private val bio: BIO[F] = implicitly
  import bio._

  private val sessionStorage = scala.collection.mutable.HashMap.empty[(UserId, String), String]

  def fetchSessionId(userId: UserId, ip: String) : F[DomainFailure, String] = {
    fromOption(DomainFailure(DomainFailureCode.EntityNotFound, None))(sessionStorage.get((userId, ip)))
  }

  def register(userId: UserId, ip: String, token: String) : F[DomainFailure, Unit] = {
    point(sessionStorage.put((userId, ip), token)).void
  }

  def remove(userId: UserId, ip: String) : F[DomainFailure, Unit] = {
    point(sessionStorage.remove((userId, ip)))
  }
}
