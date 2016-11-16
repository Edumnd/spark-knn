package com.github.saurfang.spark.ml.knn.examples

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.KNNClassifier
import org.apache.spark.ml.feature.PCA
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.log4j

object MNIST {

  val logger = log4j.Logger.getLogger(getClass)

  def main(args: Array[String]) {
    val conf = new SparkConf()
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    //read in raw label and features
    val dataset = MLUtils.loadLibSVMFile(sc, "data/mnist/mnist.bz2")
      .toDF()

    //split traning and testing
    val Array(train, test) = dataset
      .randomSplit(Array(0.7, 0.3), seed = 1234L)
      .map(_.cache())

    //create PCA matrix to reduce feature dimensions
    val pca = new PCA()
      .setInputCol("features")
      .setK(50)
      .setOutputCol("pcaFeatures")
    val knn = new KNNClassifier()
      .setTopTreeSize(dataset.count().toInt / 500)
      .setFeaturesCol("pcaFeatures")
      .setPredictionCol("predicted")
      .setK(1)
    val pipeline = new Pipeline()
      .setStages(Array(pca, knn))
      .fit(train)

    val insample = validate(pipeline.transform(train))
    val outofsample = validate(pipeline.transform(test))

    //reference accuracy: in-sample 95% out-of-sample 94%
    logger.info(s"In-sample: $insample, Out-of-sample: $outofsample")
  }

  private[this] def validate(results: DataFrame): Double = {
    results
      .selectExpr("SUM(CASE WHEN label = predicted THEN 1.0 ELSE 0.0 END) / COUNT(1)")
      .collect()
      .head
      .getDecimal(0)
      .doubleValue()
  }
}
