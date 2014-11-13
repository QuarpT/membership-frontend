package services

import scala.concurrent.Future

import play.api.libs.ws.WSRequestHolder

import model.Stripe._
import model.StripeDeserializer._
import monitoring.StripeMetrics

case class StripeApiConfig(url: String, secretKey: String, publicKey: String)

class StripeService(val apiConfig: StripeApiConfig) extends utils.WebServiceHelper[StripeObject, Error] {
  val wsUrl = apiConfig.url
  val wsMetrics = StripeMetrics

  def wsPreExecute(req: WSRequestHolder): WSRequestHolder =
    req.withHeaders("Authorization" -> s"Bearer ${apiConfig.secretKey}")

  object Customer {
    def create(identityId: String, card: String): Future[Customer] =
      post[Customer]("customers", Map("description" -> Seq(s"IdentityID - $identityId"), "card" -> Seq(card)))

    def read(customerId: String): Future[Customer] =
      get[Customer](s"customers/$customerId")

    def updateCard(customerId: String, card: String): Future[Customer] =
      post[Customer](s"customers/$customerId", Map("card" -> Seq(card)))
  }
}
