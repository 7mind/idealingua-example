package com.septimalmind.server.services.users

import com.septimalmind.services.users.UserProfileService
import com.septimalmind.services.users.UserProfileServiceServer
import com.septimalmind.server.idl.RequestContext
import com.septimalmind.services.users._
import com.septimalmind.services.shared._
import com.septimalmind.services.companies.CompanyId

class ProfileService[F[+ _, + _]] extends UserProfileServiceServer[F, RequestContext] {
  def register(ctx: RequestContext, profile: CreateUserRequest): F[DomainFailure, UserProfile]                    = ???
  def updatePublicInfo(ctx: RequestContext, id: UserId, publicData: PublicProfile): F[DomainFailure, UserProfile] = ???
  def remove(ctx: RequestContext, id: UserId): F[com.septimalmind.services.shared.DomainFailure, SuccessResponse] = ???
  def retrieveUsers(
    ctx: RequestContext,
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
