package com.septimalmind

import java.util.UUID

import com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.ClientDispatcher
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, IRTWrappedClient}
import com.septimalmind.services.auth.LoginServiceWrappedClient
import com.septimalmind.services.users.UserProfileServiceWrappedClient
import com.septimalmind.shared.RuntimeContext
import org.http4s.{Header, Request, Uri}
import scalaz.zio.IO

object client extends App with RuntimeContext {

  val uri = Uri.fromString("http://localhost:8080").right.get

  val clients: Set[IRTWrappedClient] = Set(LoginServiceWrappedClient, UserProfileServiceWrappedClient)

  val clientMultiplexor: IRTClientMultiplexor[IO] = new IRTClientMultiplexor[IO](clients)

  val clientDispatcher = new ClientDispatcher[rt.DECL](rt.self, logger, printer, uri, clientMultiplexor) {

    override protected def transformRequest(request: Request[c.MonoIO]): Request[c.MonoIO] = {
      val header = Header("Authorization", s"Api-Key company::${UUID.randomUUID()}")
      request.withHeaders(header)
    }
  }

  val loginClient = new LoginServiceWrappedClient[IO](clientDispatcher)

  val a = bio.unsafeRun(loginClient.logout())

}
