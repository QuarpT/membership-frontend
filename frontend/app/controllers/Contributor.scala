package controllers

import actions.ActionRefiners._
import actions._
import com.gu.i18n.CountryGroup
import com.gu.i18n.CountryGroup._
import com.gu.memsub.util.Timing
import com.gu.stripe.Stripe
import com.gu.stripe.Stripe.Serializer._
import com.gu.zuora.soap.models.errors._
import com.typesafe.scalalogging.LazyLogging
import controllers.Joiner._
import forms.MemberForm._
import org.joda.time.LocalDate
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Controller, Result}
import services.checkout.identitystrategy.Strategy.identityStrategyFor
import services.TouchpointBackend
import tracking.ActivityTracking
import utils.TestUsers.PreSigninTestCookie
import views.support.{PageInfo, Pricing, ThankYouMonthlySummary}

import scala.concurrent.Future
import scala.util.Failure

object Contributor extends Controller with ActivityTracking with PaymentGatewayErrorHandler
  with LazyLogging
  with CatalogProvider
  with StripeServiceProvider
  with SalesforceServiceProvider
  with SubscriptionServiceProvider
  with PaymentServiceProvider
  with MemberServiceProvider {

  def NonContributorAction = NoCacheAction andThen PlannedOutageProtection andThen authenticated() andThen onlyNonContributorFilter()

  def enterMonthlyContributionsDetails(countryGroup: CountryGroup = UK, contributionValue: Option[BigDecimal] = None) = NonContributorAction.async { implicit request =>

    implicit val resolution: TouchpointBackend.Resolution =
      TouchpointBackend.forRequest(PreSigninTestCookie, request.cookies)

    implicit val tpBackend = resolution.backend

    implicit val backendProvider: BackendProvider = new BackendProvider {
      override def touchpointBackend = tpBackend
    }

    implicit val c = catalog

    val identityRequest = IdentityRequest(request)

    (for {
      identityUser <- identityService.getIdentityUserView(request.user, identityRequest)
    } yield {
      val plans = catalog.contributor

      val pageInfo = PageInfo(
        stripePublicKey = Some(stripeService.publicKey),
        payPalEnvironment = Some(tpBackend.payPalService.config.payPalEnvironment)
      )

      Ok(views.html.joiner.form.monthlyContribution(
        plans,
        identityUser,
        pageInfo,
        countryGroup,
        resolution,
        Pricing.bigDecimalToPrice(contributionValue.getOrElse(5))))
    }).andThen { case Failure(e) => logger.error(s"User ${request.user.user.id} could not enter details for paid tier supporter: ${identityRequest.trackingParameters}", e) }
  }

  def joinMonthlyContribution = NonContributorAction.async { implicit request =>
    monthlyContributorForm.bindFromRequest.fold({ formWithErrors =>
      Future.successful(BadRequest(formWithErrors.errorsAsJson))
    },
      makeContributor(Ok(Json.obj("redirect" -> routes.Contributor.thankyouContributor().url))))
  }

  private def makeContributor(onSuccess: => Result)(formData: ContributorForm)(implicit request: AuthRequest[_]) = {
    logger.info(s"User ${request.user.id} attempting to become a monthly contributor...")
    implicit val bp: BackendProvider = request

    identityStrategyFor(request, formData).ensureIdUser { user =>
      Timing.record(salesforceService.metrics, "createContributor") {
        memberService.createContributor(user, formData).map {
          case (sfContactId, zuoraSubName) =>
            logger.info(s"User ${user.id} successfully became monthly contributor $zuoraSubName.")
            onSuccess
        }.recover {
          // errors due to user's card are logged at WARN level as they are not logic errors
          case error: Stripe.Error =>
            logger.warn(s"Stripe API call returned error: \n\t$error \n\tuser=${user.id}")
            Forbidden(Json.toJson(error))

          case error: PaymentGatewayError =>
            handlePaymentGatewayError(error, user.id, "monthly contributor")

          case error =>
            logger.error(s"User ${user.id} could not become monthly contributor member", error)
            Forbidden
        }
      }
    }
  }

  def thankyouContributor = ContributorAction.async { implicit request =>
    implicit val resolution: TouchpointBackend.Resolution = TouchpointBackend.forRequest(PreSigninTestCookie, request.cookies)

    for {
      paymentMethod <- paymentService.getPaymentMethod(request.contributor.accountId)
    } yield {
      Ok(views.html.joiner.thankyouMonthly(
        request.contributor.plan,
        ThankYouMonthlySummary(LocalDate.now(), request.contributor.plan.charges.price.prices.head, paymentMethod),
        resolution
      ))
    }
  }
}
