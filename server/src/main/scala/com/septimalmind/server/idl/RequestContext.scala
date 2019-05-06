package com.septimalmind.server.idl

import com.septimalmind.services.companies.CompanyId
import com.septimalmind.services.users.UserId

sealed trait RequestContext {
  def networkContext : NetworkContext
}

object RequestContext {
  case class AdminRequest(userId: UserId, networkContext : NetworkContext) extends RequestContext

  case class ClientRequest(userId: UserId, networkContext : NetworkContext) extends RequestContext

  case class GuestContext(networkContext : NetworkContext) extends RequestContext
}

case class NetworkContext(ip: String, clientType: String = "UNKNOWN")
