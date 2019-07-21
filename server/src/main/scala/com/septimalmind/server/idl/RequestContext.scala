package com.septimalmind.server.idl

import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.users.UserId

import scala.util.Try

sealed trait RequestContext {
  def networkContext : NetworkContext

  def asToken: Option[String]
}

object RequestContext {
  case class AdminRequest(companyId: CompanyId, networkContext: NetworkContext) extends RequestContext {
    override def asToken: Option[String] = Some(
      s"Api-Key company::${companyId.toString()}"
    )
  }

  case class ClientRequest(userId: UserId, networkContext : NetworkContext) extends RequestContext {
    override def asToken: Option[String] = {
      Some(s"Bearer ${userId.toString()}")
    }
  }

  case class GuestRequest(networkContext : NetworkContext) extends RequestContext {
    override def asToken: Option[String] = None
  }


  object ClientRequest {
    def fromHeader(token: String, networkContext: NetworkContext) : Option[ClientRequest] = {
      Try(UserId.parse(token)).map(ClientRequest(_, networkContext)).toOption
    }
  }


  object AdminRequest {
    def fromHeader(token: String, networkContext: NetworkContext) : Option[AdminRequest] = {
      token.split("::").toList match {
        case "secret" :: id :: Nil =>
          Try(CompanyId.parse(id)).map(AdminRequest(_, networkContext)).toOption
        case _ =>
          None
      }
    }
  }
}

case class NetworkContext(ip: String, clientType: String = "UNKNOWN")
