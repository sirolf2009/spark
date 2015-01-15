/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.neuralNetwork

import breeze.linalg.{DenseVector => BDV}
import org.apache.spark.mllib.classification.{ANNClassifierHelper, ClassificationModel}
import org.apache.spark.mllib.linalg.{Vector, Vectors, Matrices}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

class MLPClassifierModel(val model: MLP, val labelToIndex: Map[Double, Int])
  extends ClassificationModel with ANNClassifierHelper with Serializable {
  /**
   * Predict values for the given data set using the model trained.
   *
   * @param testData RDD representing data points to be predicted
   * @return an RDD[Double] where each entry contains the corresponding prediction
   */
  override def predict(testData: RDD[Vector]): RDD[Double] = {
    testData.map(predict)
  }

  /**
   * Predict values for a single data point using the model trained.
   *
   * @param testData array representing a single data point
   * @return predicted category from the trained model
   */
  override def predict(testData: Vector): Double = {
    val bv: BDV[Double] = testData.toBreeze.toDenseVector
    val result = Vectors.fromBreeze(model.predict(Matrices.fromBreeze(bv.toDenseMatrix.t)).toBreeze.flatten())
    outputToLabel(result)
  }
}

class MLPClassifier private(val labelToIndex: Map[Double, Int],
                            val topology: Array[Int],
                            val maxIterations: Int,
                            val batchSize: Int)
  extends ANNClassifierHelper with Serializable  {

  def run(data: RDD[LabeledPoint]): MLPClassifierModel = {
    val annData = data.map(lp => labeledPointToVectorPair(lp))
    val model = MLP.runLBFGS(annData, topology, batchSize, maxIterations, 1e-4, 0.0)
    //val model = MLP.runSGD(annData, topology, batchSize, maxIterations, 1.0, 1.0, 0.1)
    println("error:" + MLP.error(annData, model, batchSize))
    new MLPClassifierModel(model, labelToIndex)
  }

}

object MLPClassifier {

  def train(data: RDD[LabeledPoint], topology: Array[Int], maxIterations: Int,
            batchSize: Int): MLPClassifierModel = {
    val labelToIndex = data.map( lp => lp.label).distinct().collect().sorted.zipWithIndex.toMap
    new MLPClassifier(labelToIndex, topology, maxIterations, batchSize).run(data)
  }

}
