package daos

import com.rdio.simple.RdioCoreClient
import com.rdio.simple.RdioClient
import com.rdio.simple.Parameters

import helpers.Common.cfg

class RdioDao(val accessToken: String, val tokenSecret: String) {
  
  val client = new RdioCoreClient(
    new RdioClient.Consumer(cfg("rdio.consumer_key"), cfg("rdio.consumer_secret")),
    new RdioClient.Token(accessToken, tokenSecret))
  
  def call(method: String) = {
    client.call(method)
  }
}