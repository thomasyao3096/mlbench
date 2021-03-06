package optimizers

import breeze.linalg.Vector
import distopt.solvers.CoCoA
import distopt.utils.DebugParams
import utils.Functions.{CocoaLabeledPoint, LossFunction, Regularizer}

/**
  * Created by amirreza on 16/04/16.
  */
class Cocoa(val data: CocoaLabeledPoint,
            loss: LossFunction,
            regularizer: Regularizer,
            params: CocoaParameters,
            debug: DebugParams,
            plus: Boolean) extends Optimizer(loss, regularizer){
  override def optimize(): Vector[Double] = {
    val (finalwCoCoAPlus, finalalphaCoCoAPlus) = CoCoA.runCoCoA(data, params.getDistOptPar(), debug, plus)
    return finalwCoCoAPlus
  }
}