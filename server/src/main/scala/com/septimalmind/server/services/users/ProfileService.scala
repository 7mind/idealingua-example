package com.septimalmind.server.services.users

import com.github.pshirshov.izumi.functional.bio.BIO
import com.septimalmind.server.idl.RequestContext.AdminRequest
import com.septimalmind.services.users.UserProfileService
import com.septimalmind.services.users.UserProfileServiceServer
import com.septimalmind.services.users._
import com.septimalmind.services.shared._
import com.septimalmind.services.companies.CompanyId

class ProfileService[F[+ _, + _]: BIO] extends UserProfileServiceServer[F, AdminRequest] {
  def register(ctx: AdminRequest, profile: CreateUserRequest): F[DomainFailure, UserProfile]                    = ???
  def updatePublicInfo(ctx: AdminRequest, id: UserId, publicData: PublicProfile): F[DomainFailure, UserProfile] = ???
  def remove(ctx: AdminRequest, id: UserId): F[com.septimalmind.services.shared.DomainFailure, SuccessResponse] = ???
  def retrieveUsers(
    ctx: AdminRequest,
    limitOffset: com.septimalmind.services.shared.OffsetLimit,
    ordering: Option[Ord]
  ): F[DomainFailure, QueryingResponse] = ???
}

object ProfileService {
  trait UserStorage[F[+ _, + _]] {
    def create(userData: UserData): F[Throwable, UserId]
    def delete(userId: UserId): F[Throwable, Unit]
    def update(user: UserProfile): F[Throwable, Unit]
    def getById(id: UserId): F[Throwable, Option[UserProfile]]
    def getForCompany(company: CompanyId): F[Throwable, List[UserProfile]]
  }
}
