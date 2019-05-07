package com.septimalmind.server.persistence

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.septimalmind.services.shared.{DomainFailure, DomainFailureCode}
import com.septimalmind.services.users.{UserId, UserProfile}

class UserRepo[F[+_, +_]: BIO] {

  private val bio: BIO[F] = implicitly
  import bio._

  private val usersStorage = scala.collection.mutable.HashMap.empty[UserId, UserProfile]

  def registerUser(userProfile: UserProfile) : F[DomainFailure, Unit] = {
    point(usersStorage.put(userProfile.id, userProfile))
  }

  def removeUser(userId: UserId) : F[DomainFailure, Unit] = {
    point(usersStorage.remove(userId)).void
  }

  def findByEmail(email: String) : F[DomainFailure, UserId] = {
    println(email)
    println(usersStorage)
    fromOption(DomainFailure(DomainFailureCode.EntityNotFound, Some("user with email not found")))(usersStorage.find(_._2.email == email).map(_._1))
  }
}
