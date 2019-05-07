package com.septimalmind.server.services.auth
import com.septimalmind.services.users.UserId
import com.septimalmind.services.shared.DomainFailure
import com.github.pshirshov.izumi.functional.bio.BIO
import com.github.pshirshov.izumi.functional.bio.BIO._
import java.{util => ju}
import com.septimalmind.services.shared.DomainFailureCode

class PendingConfirmationRepo[F[+_, +_]: BIO] {

    private val bio: BIO[F] = implicitly
    import bio._

    protected val storage = scala.collection.mutable.HashMap.empty[ju.UUID, (UserId, String)]

    def acquire(userId: UserId, token: String) : F[DomainFailure, ju.UUID] = {
      val uid = ju.UUID.randomUUID()
      point(storage.put(uid, (userId, token))).map(_ => uid)
    }

    def fetch(acquireId: ju.UUID) : F[DomainFailure, (UserId, String)] = {
        fromOption(DomainFailure(DomainFailureCode.EntityNotFound, Some("cant' find appropriate token for requested id")))(
            storage.get(acquireId)
        )
    }

    def release(acquireId: ju.UUID) : F[DomainFailure, Unit] = {
        sync(storage.remove(acquireId)).void
    }
}
