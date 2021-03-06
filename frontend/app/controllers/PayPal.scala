package controllers

import actions.CommonActions
import com.gu.monitoring.SafeLogger
import com.gu.monitoring.SafeLogger._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{BaseController, ControllerComponents, Request}
import services.{PayPalService, TouchpointBackend, TouchpointBackends}
import utils.TestUsers.PreSigninTestCookie

import scala.concurrent.{ExecutionContext, Future}

object PayPal {
  // Payment token used to tie PayPal requests together.
  case class Token(token: String)

  case class PayPalBillingDetails(amount: Float, billingPeriod: String, currency: String, tier: String)

  // Json readers & writers.
  implicit val formatsToken = Json.format[Token]
  implicit val readsBillingDetails = Json.reads[PayPalBillingDetails]
}

class PayPal(touchpointBackends: TouchpointBackends, implicit val executionContext: ExecutionContext, commonActions: CommonActions, override protected val controllerComponents: ControllerComponents) extends BaseController with PayPalServiceProvider {

  import commonActions.NoCacheAction
  import PayPal._

  // Sets up a payment by contacting PayPal, returns the token as JSON.
  def setupPayment = NoCacheAction.async(parse.json[PayPalBillingDetails]) { implicit request =>
    readRequestAndRunServiceCall(_.retrieveToken(
      returnUrl = routes.PayPal.returnUrl().absoluteURL(secure = true),
      cancelUrl = routes.PayPal.cancelUrl().absoluteURL(secure = true)
    ))
  }

  // Creates a billing agreement using a payment token.
  def createAgreement = NoCacheAction.async(parse.json[Token]) { implicit request =>
    readRequestAndRunServiceCall(_.retrieveBaid)
  }

  //Takes a request with a body of type [T], then passes T to the payPal call 'exec' to retrieve a token and returns this as json
  def readRequestAndRunServiceCall[T](exec: (PayPalService) => ((T) => Future[String]))(implicit request: Request[T]) = {
    val payPalService = touchpointBackends.forRequest(PreSigninTestCookie, request.cookies).backend.payPalService

    for {
      token <- exec(payPalService)(request.body)
    } yield Ok(toJson(Token(token)))
  }

  // The endpoint corresponding to the PayPal return url, hit if the user is
  // redirected and needs to come back.
  def returnUrl = NoCacheAction {
    SafeLogger.error(scrub"User hit the PayPal returnUrl.")
    Ok(views.html.paypal.errorPage())
  }

  // The endpoint corresponding to the PayPal cancel url, hit if the user is
  // redirected and the payment fails.
  def cancelUrl = NoCacheAction {
    SafeLogger.error(scrub"User hit the PayPal cancelUrl, something went wrong.")
    Ok(views.html.paypal.errorPage())
  }
}
