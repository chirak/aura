package daos

import scala.Option.option2Iterable
import scala.io.Source

import helpers.Common.cfg
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
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

    return baseUrl + apiKey + format + resultLimit + artist + title + buckets
  }
  
  def getEchonestDataForTrack(artistName: String, trackName: String) = {
    val url = formatEchonestUrl(artistName, trackName)
    WS.url(url).get()
    // TODO
  }

}
