package com.septimalmind.server.services.users

import com.github.pshirshov.izumi.functional.bio.BIO
import com.septimalmind.server.idl.RequestContext.AdminRequest
import com.septimalmind.services.shared._
import com.septimalmind.services.users.{UserProfileServiceServer, _}
import scala.language.higherKinds

class ProfileService[F[+ _, + _] : BIO] extends UserProfileServiceServer[F, AdminRequest] {
  def register(ctx: AdminRequest, profile: CreateUserRequest): F[DomainFailure, UserProfile] = {
    ???
  }

  def updatePublicInfo(ctx: AdminRequest, id: UserId, publicData: PublicProfile): F[DomainFailure, UserProfile] = {
    ???
  }

  def remove(ctx: AdminRequest, id: UserId): F[DomainFailure, SuccessResponse] = {
    ???
  }

  def retrieveUsers
  (
    ctx: AdminRequest,
    limitOffset: com.septimalmind.services.shared.OffsetLimit,
    ordering: Option[Ord]
  ): F[DomainFailure, QueryingResponse] = ???
}

