package daos

import java.net.URLEncoder

import scala.Option.option2Iterable
import scala.io.Source
import scala.util.Failure
import scala.util.Success

import helpers.Common.cfg
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{ JsArray, JsString, JsValue, Json }
import play.api.libs.ws.WS

/**
 * This is meant to be ran as a stand alone script. This script consumes a Json
 * file, collects track meta data from Echonest, and will store meta data in
 * some sort of database. The consumed Json file must be in a similar format to
 * what is retrieved using the Rdio getTracksInCollection method.
 */
object EchonestDao {

  def main(args: Array[String]) {
    val rawJson = Source.fromFile(args(0)).mkString
    val trackTuples = getTrackInfoFromJson(rawJson)
    for(trackTuple <- trackTuples) {
      val futureResponse = getEchonestDataForTrack(trackTuple._1, trackTuple._2)
      futureResponse.onComplete {
        case Success(json) => println(json.toString)
        case Failure(err) =>  println(err)
      }
    }
  }

  // Returns a sequence of 2-tuples - (artist, track)
  def getTrackInfoFromJson(rawJson: String): Seq[(String, String)] = {

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

    trackTuples
  }
  
  def formatEchonestUrl(artistName: String, trackName: String) : String = {
    val baseUrl     = "http://developer.echonest.com/api/v4/song/search?"
    val apiKey      = s"api_key=${cfg("echonest.api_key")}"
    val format      = "&format=json"
    val resultLimit = "&result=1"
    val artist      = s"&artist=$artistName"
    val title       = s"&title$trackName"
    val buckets     = "&bucket=id:rdio-US&bucket=audio_summary&bucket=tracks"

    return URLEncoder.encode(baseUrl + apiKey + format + resultLimit + artist + title + buckets, "UTF-8")
  }
  
  def getEchonestDataForTrack(artistName: String, trackName: String) = {
    val url = formatEchonestUrl(artistName, trackName)
    val futureResponse = WS.url(url).get()
    val futureResult = futureResponse.map { response =>
      // We are currently capped at 20 Echonest calls per minute. On average
      // each call takes less than 200ms. That means To complete 20 requests
      // we'll only spend 4 seconds. So for now we must wait the remaining 56
      // seconds to continue making Echonest calls :(
      val apiCallLimit = response.header("X-RateLimit-Remaining").get.toInt
      if(apiCallLimit <= 0) { Thread.sleep(56000) }
      response.json
    }
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
          val track = Track(id.toString, artistName.toString, trackName.toString, summary.toString)
          Some(track)
        }
        case _ => None
      }
    }
    
    def storeTrack(track: Track) {
      
    }

    val status = json \ "response" \ "status"
    val audioSummary = getStatusCodeMsg(status)  match {
      case ("0", msg) => {
        deserializeTrack(json \ "response" \ "songs") match {
          case Some(t) => storeTrack(t)
          case None    => println("Retrieved unexpected Json format")
        }
      }
      case (_, msg)   => println(s"Could not retreive track data. Msg: $msg")
    }
  }
    
}
