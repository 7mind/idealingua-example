package com.septimalmind.server.persistence

import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import com.septimalmind.services.shared.{DomainFailure, DomainFailureCode}
import com.septimalmind.services.users.{UserId, UserProfile}
import com.septimalmind.services.auth.TwoFactorAuthFormat

class UserRepo[F[+_, +_]: BIO] {

  private val bio: BIO[F] = implicitly
  import bio._

  private val usersStorage = scala.collection.mutable.HashMap.empty[UserId, UserProfile]

  private val authPreferencesStorage = scala.collection.mutable.HashMap.empty[UserId, TwoFactorAuthFormat]

  def registerUser(userProfile: UserProfile) : F[DomainFailure, Unit] = {
    point(usersStorage.put(userProfile.id, userProfile))
  }

  def removeUser(userId: UserId) : F[DomainFailure, Unit] = {
    point(usersStorage.remove(userId)).void
  }

  def checkPw(userId: UserId, pw: String) : F[DomainFailure, Unit] = {
    for {
      curPw <- fromOption(DomainFailure(DomainFailureCode.EntityNotFound, Some("user with email not found")))(usersStorage.get(userId)).map(_.password)
      _ <- when(curPw != pw)(fail(DomainFailure(DomainFailureCode.AssertionFailed, Some("wrong password for user"))))
    } yield ()
  }

  def findByEmail(email: String) : F[DomainFailure, UserProfile] = {
    fromOption(DomainFailure(DomainFailureCode.EntityNotFound, Some("user with email not found")))(usersStorage.find(_._2.email == email).map(_._2))
  }

  def findByPhone(phone: String) : F[DomainFailure, UserProfile] = {
    fromOption(DomainFailure(DomainFailureCode.EntityNotFound, Some("user with phone not found")))(usersStorage.find(_._2.phone == phone).map(_._2))
  }
  
  def upsertAuthFormat(userId: UserId, format: TwoFactorAuthFormat) : F[Nothing, Unit] = {
    point(authPreferencesStorage.put(userId, format))
  }

  def retrieveUserAuthFormat(userId: UserId) : F[Nothing, TwoFactorAuthFormat] = {
    point(authPreferencesStorage.getOrElseUpdate(userId, TwoFactorAuthFormat.None))
  }
}
