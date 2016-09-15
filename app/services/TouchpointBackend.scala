package services

import com.gu.config.DiscountRatePlanIds
import com.gu.memsub.promo.Promotion._
import com.gu.memsub.promo.{DynamoPromoCollection, DynamoTables, PromotionCollection}
import com.gu.memsub.services.{PaymentService => CommonPaymentService, _}
import com.gu.monitoring.{ServiceMetrics, StatusMetrics}
import com.gu.salesforce.SimpleContactRepository
import com.gu.stripe.StripeService
import com.gu.subscriptions.suspendresume.SuspensionService
import com.gu.subscriptions.Discounter
import com.gu.zuora
import com.gu.zuora.rest.RequestRunners
import com.gu.zuora.{rest, soap}
import configuration.Config
import forms.SubscriptionsForm
import monitoring.TouchpointBackendMetrics
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.RequestHeader
import touchpoint.TouchpointBackendConfig.BackendType
import touchpoint.{TouchpointBackendConfig, ZuoraProperties}
import com.gu.memsub.subsv2
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalaz.std.scalaFuture._
import utils.TestUsers._

trait TouchpointBackend {
  def environmentName: String
  def salesforceService: SalesforceService
  def catalogService : subsv2.services.CatalogService[Future]
  def zuoraService: zuora.api.ZuoraService
  def subscriptionService: subsv2.services.SubscriptionService[Future]
  def zuoraRestClient: zuora.rest.Client
  def paymentService: PaymentService
  def commonPaymentService: CommonPaymentService
  def zuoraProperties: ZuoraProperties
  def promoService: PromoService
  def promoCollection: PromotionCollection
  def promoStorage: JsonDynamoService[AnyPromotion, Future]
  def discountRatePlanIds: DiscountRatePlanIds
  def suspensionService: SuspensionService[Future]
  def subsForm: SubscriptionsForm
  def exactTargetService: ExactTargetService
  def checkoutService: CheckoutService
}

object TouchpointBackend {

  private implicit val system = Akka.system

  def apply(backendType: TouchpointBackendConfig.BackendType): TouchpointBackend = {

    val config = TouchpointBackendConfig.backendType(backendType, Config.config)
    val soapClient = new soap.ClientWithFeatureSupplier(Set.empty, config.zuoraSoap, new ServiceMetrics(Config.stage, Config.appName, "zuora-soap-client"))
    val simpleRestClient = new rest.SimpleClient[Future](config.zuoraRest, RequestRunners.futureRunner)

    val newProductIds = Config.productIds(config.environmentName)
    val _stripeService = new StripeService(config.stripe, new TouchpointBackendMetrics with StatusMetrics {
      val backendEnv = config.stripe.envName
      val service = "Stripe"
    })

    new TouchpointBackend {
      lazy val environmentName = config.environmentName
      lazy val salesforceService = new SalesforceServiceImp(new SimpleContactRepository(config.salesforce, system.scheduler, Config.appName))
      lazy val catalogService = new subsv2.services.CatalogService[Future](newProductIds, simpleRestClient, Await.result(_, 10.seconds), backendType.name)
      lazy val zuoraService = new zuora.ZuoraService(soapClient, this.zuoraRestClient)
      lazy val subscriptionService = new subsv2.services.SubscriptionService[Future](newProductIds, this.catalogService.catalog.map(_.leftMap(_.list.mkString).map(_.map)), simpleRestClient, zuoraService.getAccountIds)
      lazy val zuoraRestClient = new rest.Client(config.zuoraRest, new ServiceMetrics(Config.stage, Config.appName, "zuora-rest-client"))
      lazy val paymentService = new PaymentService(_stripeService)
      lazy val commonPaymentService = new CommonPaymentService(_stripeService, zuoraService, this.catalogService.unsafeCatalog.productMap)
      lazy val zuoraProperties = config.zuoraProperties
      lazy val promoService = new PromoService(promoCollection, new Discounter(this.discountRatePlanIds))
      lazy val promoCollection = new DynamoPromoCollection(this.promoStorage)
      lazy val promoStorage = JsonDynamoService.forTable[AnyPromotion](DynamoTables.promotions(Config.config, config.environmentName))
      lazy val discountRatePlanIds = Config.discountRatePlanIds(config.environmentName)
      lazy val suspensionService = new SuspensionService[Future](Config.holidayRatePlanIds(config.environmentName), simpleRestClient)
      lazy val subsForm = new forms.SubscriptionsForm(this.catalogService.unsafeCatalog)
      lazy val exactTargetService: ExactTargetService = new ExactTargetService(this.subscriptionService, this.commonPaymentService, this.zuoraService, this.salesforceService)
      lazy val checkoutService: CheckoutService = new CheckoutService(IdentityService, this.salesforceService, this.paymentService, this.catalogService.unsafeCatalog, this.zuoraService, this.exactTargetService, this.zuoraProperties, this.promoService, this.discountRatePlanIds)
    }
  }

  val BackendsByType = BackendType.All.map(typ => typ -> TouchpointBackend(typ)).toMap

  val Normal = BackendsByType(BackendType.Default)
  val Test = BackendsByType(BackendType.Testing)
  val All = BackendsByType.values.toSeq

  case class Resolution(
    backend: TouchpointBackend,
    typ: TouchpointBackendConfig.BackendType,
    validTestUserCredentialOpt: Option[TestUserCredentialType[_]]
  )

  /**
   * Alternate credentials are used *only* when the user is not signed in - if you're logged in as
   * a 'normal' non-test user, it doesn't make any difference what pre-signin-test-cookie you have.
   */
  def forRequest[C](permittedAltCredentialType: TestUserCredentialType[C], altCredentialSource: C)(
    implicit request: RequestHeader): Resolution = {
    val validTestUserCredentialOpt = isTestUser(permittedAltCredentialType, altCredentialSource)
    val backendType = if (validTestUserCredentialOpt.isDefined) BackendType.Testing else BackendType.Default
    Resolution(BackendsByType(backendType), backendType, validTestUserCredentialOpt)
  }
}

