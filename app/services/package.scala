import com.gu.identity.play.IdMinimalUser
import play.api.libs.functional.syntax._
import play.api.libs.json._

package object services {

  case class AuthCookie(value: String)

  case class UserId(id: String) {
    override def toString = id
  }

  object UserId {
    val paramName = "user-id"

    implicit val jsonReader = new Reads[UserId] {
      override def reads(json: JsValue): JsResult[UserId] = json match {
        case JsString(id) => JsSuccess(UserId(id))
        case _            => JsError("Cannot parse UserId")
      }
    }
  }

  case class IdentityToken(token: String) {
    override def toString = token
  }

  object IdentityToken {
    val paramName = "identity-token"

    implicit val jsonReader = new Reads[IdentityToken] {
      override def reads(json: JsValue): JsResult[IdentityToken] = json match {
        case JsString(id) => JsSuccess(IdentityToken(id))
        case _            => JsError("Cannot parse IdentityToken")
      }
    }
  }

  sealed trait UserIdData {
    def id: UserId
  }

  case class MinimalIdUser(user: IdMinimalUser) extends UserIdData {
    override def id = UserId(user.id)
  }

  case class GuestUser(id: UserId, token: IdentityToken) extends UserIdData {
    def toFormParams = Map(
      UserId.paramName -> id.toString,
      IdentityToken.paramName -> token.toString
    )
  }

  object GuestUser {
    implicit val guestUserTokenReads: Reads[GuestUser] = (
      (JsPath \  "guestRegistrationRequest" \ "userId").read[UserId] and
      (JsPath \  "guestRegistrationRequest" \ "token").read[IdentityToken]
    )(GuestUser.apply _)
    class UnparsableGuestUserError extends RuntimeException("Cannot de-serialise GuestUser data from session")
  }
}
