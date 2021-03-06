package optimizers

import breeze.linalg.DenseVector
import breeze.numerics.sqrt
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import utils.Functions.{LossFunction, Regularizer}

import scala.util.Random


/**
  * Created by amirreza on 09/03/16.
  */


class SGD(val data: RDD[LabeledPoint],
          loss: LossFunction,
          regularizer: Regularizer,
          params: SGDParameters) extends Optimizer(loss, regularizer) {


  override def optimize(): DenseVector[Double] = {
    val d: Int = data.first().features.size //feature dimension
    val n: Double = data.count() //dataset size
    var gamma: Double = params.stepSize

    //Initial weight vector
    var w: DenseVector[Double] = DenseVector.fill(d) {
      0.0
    }

    //TODO: Isn't this inefficient ??!! I think it is inefficient unless number of datapoints per partition is not a lot
    val dataArr:RDD[Array[LabeledPoint]] = data.mapPartitions(p => Iterator(p.toArray)).cache()
    for (i <- 1 to params.iterations) {
      gamma = params.stepSize / sqrt(i)
      val loss_gradient = dataArr.mapPartitions(partitionUpdate(_, w, params.miniBatchFraction, params.seed)).reduce(_ + _)
      val reg_gradient: DenseVector[Double] = regularizer.subgradient(w) * n
      w -= gamma * (loss_gradient + regularizer.lambda * reg_gradient)
    }

    return w;
  }

  private def partitionUpdate(localData: Iterator[Array[LabeledPoint]],
                              w: DenseVector[Double],
                              fraction: Double,
                              seed: Int): Iterator[DenseVector[Double]] = {
    val dataArray: Array[LabeledPoint] = localData.next() //Get the array
    val n: Int = dataArray.length //local dataset size
    val subSetSize: Int = (n * fraction).toInt //size of randomely selected samples
    val indices: Seq[Int] = Array.range(0, n)
    val r = new Random(seed)
    require(subSetSize > 0, "fraction is too small: " + fraction)

    //Randomely sample local dataset
    val subSet: Seq[LabeledPoint] = (fraction: Double) match {
      case 1 => dataArray //GD case
      case _ => {         //optimizers.SGD case
        val shuffeledIndices = r.shuffle(indices).take(subSetSize)
        shuffeledIndices.map(dataArray)
      }
    }

    val res = subSet.map(p =>
      loss.subgradient(w, DenseVector(p.features.toArray), p.label)).reduce(_ + _)
    return Iterator(res)
  }
}

