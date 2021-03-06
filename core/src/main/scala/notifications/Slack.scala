//: ----------------------------------------------------------------------------
//: Copyright (C) 2017 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package nelson
package notifications

import scalaz.{~>, Free, Coyoneda,@@}
import scalaz.syntax.traverse._
import scalaz.std.list._
import scalaz.concurrent.Task
import dispatch._, Defaults._


sealed abstract class SlackOp[A] extends Product with Serializable

object SlackOp {

  type SlackOpF[A] = Free.FreeC[SlackOp, A]

  type SlackOpC[A] = Coyoneda[SlackOp, A]

  final case class SendSlackNotification(channels: List[SlackChannel], message: String) extends SlackOp[Unit]

  def send(channels: List[SlackChannel], msg: String): SlackOpF[Unit] =
    Free.liftFC(SendSlackNotification(channels, msg))
}

final class SlackHttp(cfg: SlackConfig, http: Http) extends (SlackOp ~> Task) {
  import argonaut._, Argonaut._
  import delorean._
  import SlackOp._

  def apply[A](op: SlackOp[A]): Task[A] = op match {
    case SendSlackNotification(channels, msg) =>
      channels.traverse(channel => send(channel,msg)).map(_ => ())
  }

  def send(channel: String, msg: String): Task[Unit] = {
    val json = Json("channel" := "#"+channel, "text" := msg, "username" := cfg.username).asJson.nospaces
    val req = url(cfg.webhook).setContentType("application/json", "UTF-8") << json
    http(req OK as.String).toTask.map(_ => ())
  }
}

