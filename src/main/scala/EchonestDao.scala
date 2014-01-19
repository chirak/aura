import java.net.URLEncoder

import scala.io.Source
import scala.Option.option2Iterable
import scala.util.Failure
import scala.util.Success

import com.redis.RedisClient

import helpers.Common.cfg
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.Logger
import play.api.Logger._
import play.core.StaticApplication

/**
 * This is meant to be ran as a stand alone script. This script consumes a Json
 * file, collects track meta data from Echonest, and will store meta data in
 * some sort of database. The consumed Json file must be in a similar format to
 * what is retrieved using the Rdio getTracksInCollection method.
 */
object EchonestDao {

  def main(args: Array[String]) {
    val app = new StaticApplication(new java.io.File("."))
    val rawJson = Source.fromFile(args(0)).mkString
    val trackTuples = getTrackInfoFromJson(rawJson)
    storeEchonestDataForTracks(trackTuples, 20)
  }

  // Returns a sequence of 2-tuples - (artist, track)
  def getTrackInfoFromJson(rawJson: String): List[(String, String)] = {

    def extractJsString(jsVal: JsValue) = {
      jsVal match {
        case JsString(str) => Some(str)
        case _ => None
      }
    }

    val trackBlobs = Json.parse(rawJson) \ "result" match {
      case JsArray(elements) => elements
      case _ => throw new IllegalArgumentException("Expected Json array.")
    }

    // We are protecting ourselves from malformed data by making sure the
    // artist name and track name are in each track object.
    val trackTuples = trackBlobs.flatMap { blob =>
      val artistName = extractJsString(blob \ "albumArtist")
      val trackName = extractJsString(blob \ "name")
      val tuple = (artistName, trackName)
      tuple match {
        case (Some(a), Some(t)) => Some((a, t))
        case _ => None
      }
    }

    trackTuples.toList
  }
  
  def formatEchonestUrl(artistName: String, trackName: String) : String = {
    val encodedArtistName = URLEncoder.encode(artistName, "UTF-8")
    val encodedTrackName  = URLEncoder.encode(trackName, "UTF-8")

    val baseUrl     = "http://developer.echonest.com/api/v4/song/search?"
    val apiKey      = s"api_key=${cfg("echonest.api_key")}"
    val format      = "&format=json"
    val resultLimit = "&results=1"
    val artist      = s"&artist=$encodedArtistName"
    val title       = s"&title=$encodedTrackName"
    val buckets     = "&bucket=id:rdio-US&bucket=audio_summary&bucket=tracks"

    val url = baseUrl + apiKey + format + resultLimit + artist + title + buckets
    Logger.info(s"Formatted url for artist - $artistName, track - $trackName: " + url)
    return url
  }
  
  def getEchonestDataForTrack(artistName: String, trackName: String) = {
    val url = formatEchonestUrl(artistName, trackName)
    val futureResponse = WS.url(url).get()
    val futureResult = futureResponse.map { response => response.json }
    futureResult
  }
  
  case class Track(id: String, artistName: String, trackName: String, summary: String)
  def storeEchonestDataForTrack(json: JsValue) {

    def getStatusCodeMsg(json : JsValue) : (String, String) = {
      val code = json \ "code"
      val msg  = json \ "message"
      (code.toString, msg.toString)
    }
    
    def deserializeTrack(json: JsValue) = {
      json match {
        case JsArray(arr) => {
          val trackJson = arr(0)
          val id          = trackJson \ "id"
          val artistName  = trackJson \ "artist_name"
          val trackName   = trackJson \ "title"
          val summary     = trackJson \ "audio_summary"
          val track = Track(id.as[JsString].toString, artistName.toString, trackName.toString, summary.toString)
          Some(track)
        }
        case _ => None
      }
    }
    
    def storeTrack(track: Track) {
      val redisClient = new RedisClient("localhost", 6379)
      val trackMap = Map("artist" -> track.artistName, "track" -> track.trackName, "summary" -> track.summary)
      val success = redisClient.hmset(track.id, trackMap)
      
      if (!success) {
        Logger.error("Error occured while storing track summary")
      }
    }

    val status = json \ "response" \ "status"
    val audioSummary = getStatusCodeMsg(status)  match {
      case ("0", msg) => {
        deserializeTrack(json \ "response" \ "songs") match {
          case Some(t) => storeTrack(t)
          case None    => Logger.error("Retrieved unexpected Json format")
        }
      }
      case (_, msg)   => Logger.error(s"Could not retreive track data. Msg: $msg")
    }
  }
  
  def storeEchonestDataForTracks(trackTuples: Seq[(String, String)], limit: Integer) {
    trackTuples match {
      case Nil => Logger.info("Finished storing Echonest audio summaries")
      case first::rest => {
        if (limit <= 0) {
	      // We are currently capped at 20 Echonest calls per minute. On average
	      // each call takes less than 200ms. That means To complete 20 requests
	      // we'll only spend 4 seconds. So for now we must wait the remaining 56
	      // seconds to continue making Echonest calls :(
          Thread.sleep(58000)
          storeEchonestDataForTracks(trackTuples, 20)
        } else {
          val futureResponse = getEchonestDataForTrack(first._1, first._2)
          futureResponse.onComplete {
            case Success(json) => storeEchonestDataForTrack(json)
            case Failure(err) =>  Logger.error(err.toString)
          }
          storeEchonestDataForTracks(rest, limit - 1)
        }
      }
    }
  }
}
