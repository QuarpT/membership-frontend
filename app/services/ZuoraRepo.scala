package services

import com.gu.membership.zuora.ZuoraApiConfig
import com.gu.membership.zuora.soap.Zuora.Authentication
import com.gu.membership.zuora.soap.ZuoraDeserializer._
import com.gu.membership.zuora.soap.{Login, ZuoraApi}
import com.gu.monitoring.ZuoraMetrics
import configuration.Config
import utils.ScheduledTask

import scala.concurrent.duration._

class ZuoraRepo(zuoraApiConfig: ZuoraApiConfig) extends ZuoraApi {

  override val apiConfig: ZuoraApiConfig = zuoraApiConfig

  override implicit def authentication: Authentication = authTask.get()

  override val application: String = Config.appName
  override val stage: String = Config.stage

  override val metrics = new ZuoraMetrics(stage, application)
  val authTask = ScheduledTask(s"Zuora ${apiConfig.envName} auth", Authentication("", ""), 0.seconds, 30.minutes)(request(Login(apiConfig)))
}

