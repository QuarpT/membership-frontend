package services

import scala.concurrent.Future
import model.{ EBResponse, EBEvent }
import play.api.libs.ws._
import model.EventbriteDeserializer._
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.ConfigFactory
import configuration.Config

trait EventbriteService {

  val eventListUrl: String
  val eventUrl: String
  val token: (String, String)

  def getAllEvents: Future[Seq[EBEvent]] = eventbriteRequest(eventListUrl).map(asEBEventSequence(_))

  def getEvent(id: String): Future[EBEvent] = eventbriteRequest(eventUrlWith(id)).map(asEBEvent(_))

  def eventbriteRequest(url: String): Future[Response] = WS.url(url).withQueryString(token).get()

  private def eventUrlWith(id: String) = eventUrl + s"/$id"

  private def asEBEvent(r: Response) = r.json.as[EBEvent]

  private def asEBEventSequence(r: Response) = r.json.as[EBResponse].events
}

object EventbriteService extends EventbriteService {
  val eventListUrl: String = Config.eventListUrl
  val eventUrl: String = Config.eventUrl
  val token: (String, String) = Config.eventToken
}

