package gatlin

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class AskActionBuilder(val message: AskMessage) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    new AskAction(next, message, ctx.coreComponents.statsEngine, ctx.system.dispatcher)
  }
}
