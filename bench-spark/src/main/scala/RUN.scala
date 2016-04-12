import Classification.{L2_LR_SGD, L2_SVM_SGD}
import org.apache.spark.mllib.classification._
import org.apache.spark.mllib.optimization.{L1Updater, SimpleUpdater, SquaredL2Updater, Updater}
import Functions._
import breeze.linalg.DenseVector
import org.apache.log4j.{Level, Logger}
import utils.Utils

//Load function
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

import org.apache.spark._

object RUN {

  def main(args: Array[String]) {
    //Spark conf
    val conf = new SparkConf().setAppName("Distributed Machine Learning").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)

    //Turn off logs
    val rootLogger = Logger.getRootLogger()
    rootLogger.setLevel(Level.ERROR)

    //Load data
    val dataset = "iris.scale.txt"
    val numPartitions = 4
    val points = Utils.loadLibSVMForBinaryClassification(dataset, numPartitions, sc)

    //Set optimizer's parameters
    val params = new Parameters(
      iterations = 100,
      miniBatchFraction = 1.0,
      stepSize = 0.1,
      seed = 13
    )
    //Regularization parameter
    val lambda = 0.5
    val reg = new L2Regularizer(lambda = lambda)

    //Fit with Mllib in order to compare
    runLRWithMllib(points, reg, lambda, params.iterations, params.miniBatchFraction, params.stepSize)
    println("----------------------------")
    runSVMWithMllib(points, reg, lambda, params.iterations, params.miniBatchFraction, params.stepSize)
    println("----------------------------")

    //Classify with Binary Logistic Regression
    val lr = new L2_LR_SGD(lambda, params)
    val w1 = lr.train(points)
    val objective1 = lr.getObjective()
    val error1 = lr.fiveFoldCV(points)
    println("Logistic w: " + w1)
    println("Logistic Objective value: " + objective1)
    println("Logistic CV error: " + error1)
    println("----------------------------")

    //Classify with SVM
    val svm = new L2_SVM_SGD(lambda, params)
    val w2 = svm.train(points)
    val object2 = svm.getObjective()
    val error2 = svm.fiveFoldCV(points)
    println("SVM w: " + w2)
    println("SVM Ovjective value: " + object2)
    println("SVM CV error: " + error2)
    println("----------------------------")


    sc.stop()
  }

  def runLRWithMllib(data: RDD[LabeledPoint],
                     regularizer: Regularizer,
                     lambda: Double,
                     iterations: Int,
                     fraction: Double,
                     stepSize: Double): Unit = {

    val reg: Updater = (regularizer: Regularizer) match {
      case _: L1Regularizer => new L1Updater
      case _: L2Regularizer => new SquaredL2Updater
      case _: Unregularized => new SimpleUpdater
    }
    val training = data.map(p => if (p.label == -1.0) LabeledPoint(0.0, p.features)
    else LabeledPoint(1.0, p.features)).cache()

    //Logistic Regression
    val lr = new LogisticRegressionWithSGD()
    lr.setIntercept(false)
    lr.optimizer.
      setNumIterations(iterations).
      setRegParam(lambda).
      setUpdater(reg).
      setMiniBatchFraction(fraction).
      setStepSize(stepSize)
    val lrModel = lr.run(training)

    val eval2 = new Evaluation(new BinaryLogistic, regularizer = regularizer, lambda = lambda)
    val object2 = eval2.getObjective(DenseVector(lrModel.weights.toArray), training)
    println("Mllib Logistic w: " + DenseVector(lrModel.weights.toArray))
    println("Mllib Logistic Objective value: " + object2)

    //Logistic Cross validation
    val Array(d1, d2, d3, d4, d5) = training.randomSplit(Array(0.2, 0.2, 0.2, 0.2, 0.2))

    val train1 = d1.union(d2.union(d3.union(d4)))
    val test1 = d5
    val m1 = lr.run(train1)
    val res1 = test1.map { case LabeledPoint(label, features) =>
      val prediction = m1.predict(features)
      (prediction, label)
    }
    val error1: Double = res1.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test1.count()

    val train2 = d2.union(d3.union(d4.union(d5)))
    val test2 = d1
    val m2 = lr.run(train2)
    val res2 = test2.map { case LabeledPoint(label, features) =>
      val prediction = m2.predict(features)
      (prediction, label)
    }
    val error2: Double = res2.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test2.count()

    val train3 = d3.union(d4.union(d5.union(d1)))
    val test3 = d2
    val m3 = lr.run(train3)
    val res3 = test3.map { case LabeledPoint(label, features) =>
      val prediction = m3.predict(features)
      (prediction, label)
    }
    val error3: Double = res3.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test3.count()

    val train4 = d4.union(d5.union(d1.union(d2)))
    val test4 = d3
    val m4 = lr.run(train4)
    val res4 = test4.map { case LabeledPoint(label, features) =>
      val prediction = m4.predict(features)
      (prediction, label)
    }
    val error4: Double = res4.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test4.count()

    val train5 = d5.union(d1.union(d2.union(d3)))
    val test5 = d4
    val m5 = lr.run(train5)
    val res5 = test5.map { case LabeledPoint(label, features) =>
      val prediction = m5.predict(features)
      (prediction, label)
    }
    val error5: Double = res5.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test5.count()
    val error: Double = (error1 + error2 + error3 + error4 + error5) / 5.0
    println("Mllib Logistic CV error: " + error)

  }

  def runSVMWithMllib(data: RDD[LabeledPoint],
                      regularizer: Regularizer,
                      lambda: Double,
                      iterations: Int,
                      fraction: Double,
                      stepSize: Double): Unit = {

    val reg: Updater = (regularizer: AnyRef) match {
      case _: L2Regularizer => new SquaredL2Updater
      case _: Unregularized => new SimpleUpdater
    }
    val training = data.map(p => if (p.label == -1.0) LabeledPoint(0.0, p.features)
    else LabeledPoint(1.0, p.features)).cache()


    //SVM:
    val svm = new SVMWithSGD()
    svm.setIntercept(false)
    svm.optimizer.
      setNumIterations(iterations).
      setMiniBatchFraction(fraction).
      setRegParam(lambda).
      setUpdater(reg).
      setStepSize(stepSize)
    val svmModel = svm.run(training)


    val eval = new Evaluation(new HingeLoss, regularizer, lambda = lambda)
    val object1 = eval.getObjective(DenseVector(svmModel.weights.toArray), training)
    println("Mllib SVM w: " + DenseVector(svmModel.weights.toArray))
    println("Mllib SVM Ovjective value: " + object1)

    //SVM Cross validation
    val Array(d1, d2, d3, d4, d5) = training.randomSplit(Array(0.2, 0.2, 0.2, 0.2, 0.2))

    val train1 = d1.union(d2.union(d3.union(d4)))
    val test1 = d5
    val m1 = svm.run(train1)
    val res1 = test1.map { case LabeledPoint(label, features) =>
      val prediction = m1.predict(features)
      (prediction, label)
    }
    val error1: Double = res1.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test1.count()

    val train2 = d2.union(d3.union(d4.union(d5)))
    val test2 = d1
    val m2 = svm.run(train2)
    val res2 = test2.map { case LabeledPoint(label, features) =>
      val prediction = m2.predict(features)
      (prediction, label)
    }
    val error2: Double = res2.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test2.count()

    val train3 = d3.union(d4.union(d5.union(d1)))
    val test3 = d2
    val m3 = svm.run(train3)
    val res3 = test3.map { case LabeledPoint(label, features) =>
      val prediction = m3.predict(features)
      (prediction, label)
    }
    val error3: Double = res3.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test3.count()

    val train4 = d4.union(d5.union(d1.union(d2)))
    val test4 = d3
    val m4 = svm.run(train4)
    val res4 = test4.map { case LabeledPoint(label, features) =>
      val prediction = m4.predict(features)
      (prediction, label)
    }
    val error4: Double = res4.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test4.count()

    val train5 = d5.union(d1.union(d2.union(d3)))
    val test5 = d4
    val m5 = svm.run(train5)
    val res5 = test5.map { case LabeledPoint(label, features) =>
      val prediction = m5.predict(features)
      (prediction, label)
    }
    val error5: Double = res5.map(p => if (p._1 != p._2) 1.0 else 0.0).reduce(_ + _) / test5.count()
    val error: Double = (error1 + error2 + error3 + error4 + error5) / 5.0
    println("Mllib SVM CV error: " + error)
  }
}