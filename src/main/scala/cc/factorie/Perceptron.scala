package cc.factorie

import cc.factorie.util.Implicits._
import scalala.Scalala._
import scalala.tensor.dense.DenseVector
import scalala.tensor.sparse.SparseVector
import scalala.tensor.Vector
import scala.reflect.Manifest


trait GenericPerceptronLearningModel extends Model {
	var gatherAverageWeights = false
	var useAverageWeights = false

	/**What iteration number */
	def perceptronIteration: Double

	trait GenericPerceptronLearning extends Template with LogLinearScoring {
		type TemplateType <: GenericPerceptronLearning
		// lazy val weightsSum = { freezeDomains ; new DenseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		def weightsSum: Vector
		// lazy val lastUpdateIteration = { freezeDomains ; new DenseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		def lastUpdateIteration: Vector

		var weightsDivisor = 1.0

		def increment(f: GenericPerceptronLearning#Factor, rate: Double) = {
			if (gatherAverageWeights) {
				for (i <- f.vector.activeDomain) {
					val iterationDiff = perceptronIteration - lastUpdateIteration(i)
					assert(iterationDiff >= 0)
					if (iterationDiff > 0) {
						weightsSum(i) += weights(i) * iterationDiff + f.vector(i) * rate
						lastUpdateIteration(i) = perceptronIteration
					} else
						weightsSum(i) += f.vector(i) * rate
				}
			}
			weights += f.vector * rate
			//Console.println("GenericPerceptronLearning increment weights.size="+weights.activeDomain.size)
		}

		def averageWeights = weightsSum :/ lastUpdateIteration

		override def score(s: S) =
			if (useAverageWeights)
				averageWeights dot s.vector
			else
				weights dot s.vector

		def setWeightsToAverage = weights := averageWeights
	}

	trait GenericDensePerceptronLearning extends DenseLogLinearScoring with GenericPerceptronLearning {
		type TemplateType <: GenericDensePerceptronLearning
		// lazy val weightsSum = { freezeDomains ; new DenseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		private var _weightsSum: DenseVector = null

		def weightsSum: DenseVector = {
			if (_weightsSum == null) {
				freezeDomains
				_weightsSum = new DenseVector(suffsize)
			}
			_weightsSum
		}
		// lazy val lastUpdateIteration = { freezeDomains ; new DenseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		private var _lastUpdateIteration: DenseVector = null

		def lastUpdateIteration: DenseVector = {
			if (_lastUpdateIteration == null) {
				freezeDomains
				_lastUpdateIteration = new DenseVector(suffsize)
			}
			_lastUpdateIteration
		}
	}


	trait GenericSparsePerceptronLearning extends SparseLogLinearScoring with GenericPerceptronLearning {
		type TemplateType <: GenericSparsePerceptronLearning
		// lazy val weightsSum = { freezeDomains ; new DenseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		private var _weightsSum: SparseVector = null

		def weightsSum: SparseVector = {
			if (_weightsSum == null) {
				_weightsSum = new SparseVector(suffsize)
			}
			_weightsSum
		}
		// lazy val lastUpdateIteration = { freezeDomains ; new SparseVector(suffsize) } // TODO Why doesn't this work on MacOS?
		private var _lastUpdateIteration: SparseVector = null

		def lastUpdateIteration: SparseVector = {
			if (_lastUpdateIteration == null) {
				_lastUpdateIteration = new SparseVector(suffsize)
			}
			_lastUpdateIteration
		}
	}

}





trait GibbsPerceptronLearning extends GenericPerceptronLearningModel with GibbsSampling {
	this: Model =>

	def perceptronIteration: Double = iterations.toDouble // from GibbsSampling

	trait AbstractPerceptronLearning extends GenericPerceptronLearning
	trait PerceptronLearning extends AbstractPerceptronLearning with GenericDensePerceptronLearning
	trait SparsePerceptronLearning extends AbstractPerceptronLearning with GenericSparsePerceptronLearning

	class GibbsPerceptronLearner extends GibbsSampler {
		var learningRate = 1.0

		/**Sample and learning over many variables for numIterations. */
		def sampleAndLearn[X](variables: Iterable[CoordinatedEnumVariable[X]], numIterations: Int): Unit = {
			//Console.println ("GibbsPerceptronLearning sampleAndLearn #variables="+variables.toSeq.size)
			xsample(variables, numIterations, sampleAndLearn1 _)
		}

		/**Sample one variable once, and potentially train from the jump. */
		def sampleAndLearn1[T](variable: CoordinatedEnumVariable[T]): Unit = {
			case class Proposal(modelScore: Double, trueScore: Double, value: T, diff: DiffList)
			val proposals =
			for (value <- variable.domain) yield {
				val diff = new DiffList
				variable.set(value)(diff)
				var trueScore = variable.trueScore
				val modelScore = diff.scoreAndUndo
				trueScore -= variable.trueScore
				Proposal(modelScore, trueScore, value, diff)
			}
			val (bestScoring, bestScoring2) = proposals.max2(_ modelScore)
			val (bestTruth1, bestTruth2) = proposals.max2(_ trueScore)
			/*
Console.println ("bestTruth1   trueScore = "+bestTruth1.trueScore+" value = "+bestTruth1.value)
Console.println ("bestScoring  trueScore = "+bestScoring.trueScore+" value = "+bestScoring.value)
Console.println ("bestTruth1  modelScore = "+bestTruth1.modelScore)
Console.println ("bestTruth2  modelScore = "+bestTruth2.modelScore)
Console.println ("bestScoring modelScore = "+bestScoring.modelScore)
*/
			// Only do learning if the trueScore has a preference
			// It would not have a preference if the variable in question is unlabeled
			if (bestTruth1.trueScore != bestTruth2.trueScore) {
				// If the model doesn't score the truth highest, then update parameters
				if (bestScoring.value != bestTruth1.value) {
					def m[T](implicit m: Manifest[T]) = m.erasure
					//Console.println ("Learning from error")
					//Console.println ("Model template assignable "+modelTemplates.map(t=>t.getClass.isAssignableFrom(m[PerceptronLearning])))
					//Console.println ("Model template assignable "+modelTemplates.map(t=>m[PerceptronLearning].isAssignableFrom(t.getClass)))
					//Console.println ("Model template assignable "+modelTemplates.map(t=>m[Variable].isAssignableFrom(t.getClass)))
					//Console.println ("Model templates "+modelTemplates.map(t=>t.toString+" "))
					//Console.println ("Model templates of PerceptronLearning "+modelTemplatesOf[PerceptronLearning].toSeq.size)
					//Console.println ("PerceptronLearning factors "+bestTruth1.diff.factorsOf[PerceptronLearning].toSeq.size)
					// ...update parameters by adding sufficient stats of truth, and subtracting error
					//
					bestTruth1.diff.redo
					bestTruth1.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
					bestTruth1.diff.undo
					bestScoring.diff.redo
					bestScoring.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestScoring.diff.undo
				}
				else if (bestScoring.modelScore - bestScoring2.modelScore < learningMargin) {
					//Console.println ("Learning from margin")
					// ...update parameters by adding sufficient stats of truth, and subtracting runner-up
					bestTruth1.diff.redo
					bestTruth1.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
					bestTruth1.diff.undo
					bestTruth2.diff.redo
					bestTruth2.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestTruth2.diff.undo
				}
			} //else Console.println ("No preference unlabeled "+variable)
			// Set the variable to the value the model thinks is best
			bestScoring.diff.redo // TODO consider sampling here instead?
			// Populate and manage the size of the priority queue
			if (useQueue && maxQueueSize > 0) {
				queue ++= bestScoring.diff.factors
				if (queue.size > maxQueueSize) queue.reduceToSize(maxQueueSize)
			}
		}

		/**Sample one variable once, and potentially train from the jump.  Assumes that we always start from the truth. */
		def contrastiveDivergenceSampleAndLearn1[T](variable: CoordinatedEnumVariable[T]): Unit = {
			val origIndex = variable.index
			val diff = this.sample1(variable)
			if (origIndex != variable.index) {
				// The sample wandered from truth
				diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
				diff.undo
				diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
			}
		}
	}


}

trait MHPerceptronLearning extends GenericPerceptronLearningModel with MHSampling {
	def perceptronIteration: Double = iterations.toDouble // from MHSampling

	trait AbstractPerceptronLearning extends GenericPerceptronLearning
	trait PerceptronLearning extends AbstractPerceptronLearning with GenericDensePerceptronLearning
	trait SparsePerceptronLearning extends AbstractPerceptronLearning with GenericSparsePerceptronLearning

	trait MHPerceptronLearner extends MHSampler {
		var difflist: DiffList = null
		var modelScoreRatio = 0.0
		var modelTransitionRatio = 0.0

		// Meta-parameters for learning
		var useAveraged = true
		var learningRate = 1.0;

		// Various learning diagnostics
		var bWeightsUpdated = false;
		var bFalsePositive = false;
		var bFalseNegative = false;
		var numUpdates = 0; // accumulates

		def mhPerceptronPostProposalHook: Unit = {}

		def sampleAndLearn(numIterations: Int): Unit = {
			for (iteration <- 0 until numIterations)
				sampleAndLearn1
		}

		def sampleAndLearn1: Unit = {
			incrementIterations
			difflist = new DiffList
			// Jump until difflist has changes
			while (difflist.size <= 0) modelTransitionRatio = mhJump(difflist)
			newTruthScore = difflist.trueScore
			modelScoreRatio = difflist.scoreAndUndo
			oldTruthScore = difflist.trueScore
			modelRatio = modelScoreRatio // + modelTransitionRatio
			bWeightsUpdated = false
			bFalsePositive = false;
			bFalseNegative = false;
			//        Console.println ("old truth score = "+oldTruthScore)
			//        Console.println ("new truth score = "+newTruthScore)
			//        Console.println ("modelScoreRatio = "+modelScoreRatio)
			if (newTruthScore > oldTruthScore && modelRatio <= 0) {
				//          Console.println ("Learning from error: new actually better than old.  DiffList="+difflist.size)
				difflist.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
				difflist.redo
				difflist.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
				difflist.undo
				bWeightsUpdated = true
				bFalseNegative = true
			}
			else if (newTruthScore < oldTruthScore && modelRatio >= 0) {
				//          Console.println ("Learning from error: old actually better than new.  DiffList="+difflist.size)
				difflist.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
				difflist.redo
				difflist.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
				difflist.undo
				bWeightsUpdated = true
				bFalsePositive = true
			}
			if (bWeightsUpdated) numUpdates += 1;
			//worldFactors.foreach(f => Console.println (f.toString+" weights = "+f.weights.toList))
			// Now simply sample according to the model, no matter how imperfect it is
			logAccProb = (modelScoreRatio / temperature) + modelTransitionRatio
			if (logAccProb > Math.log(random.nextDouble)) {
				if (modelRatio < 0) {
					//	    Console.print("\\")
					numNegativeMoves += 1
				}
				numAcceptedMoves += 1
				jumpAccepted = true;
				//	  Console.println("iteration: " + iteration + ", pRatio = " + pRatio);
				difflist.redo
			}
			mhPerceptronPostProposalHook
		}

		def sampleAndLearn(proposableVariables: Seq[Variable with MultiProposer]): Unit = {
			for (variable <- proposableVariables)
				sampleAndLearn1(variable)
		}

		def sampleAndLearn1(variable: Variable with MultiProposer): Unit = {
			incrementIterations
			difflist = new DiffList
			val proposals = variable.multiPropose(difflist)
			if (proposals.size < 2) {
				// Don't bother when there is only one possible proposal
				// TODO is this right?  Yes, if it is common for multiPropose to also return a proposal for "no change
				assert(difflist.size == 0)
				return difflist
			}
			val (bestScoring, bestScoring2) = proposals.max2(_ modelScore)
			val (bestTruth1, bestTruth2) = proposals.max2(_ trueScore)
			/*
Console.println ("bestTruth1   trueScore = "+bestTruth1.trueScore+" value = "+bestTruth1.value)
Console.println ("bestScoring  trueScore = "+bestScoring.trueScore+" value = "+bestScoring.value)
Console.println ("bestTruth1  modelScore = "+bestTruth1.modelScore)
Console.println ("bestTruth2  modelScore = "+bestTruth2.modelScore)
Console.println ("bestScoring modelScore = "+bestScoring.modelScore)
*/
			// Only do learning if the trueScore has a preference
			if (bestTruth1.trueScore != bestTruth2.trueScore) {
				// There is a preference among trueScores
				if (!(bestScoring eq bestTruth1)) {
					// The model's best is not the same as the truth's best
					// ...update parameters by adding sufficient stats of truth, and subtracting error
					//Console.println ("Learning from error")
					bestTruth1.diff.redo
					bestTruth1.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
					bestTruth1.diff.undo
					bestTruth1.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestScoring.diff.redo
					bestScoring.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestScoring.diff.undo
					bestScoring.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, +learningRate))
				}
				else if (bestScoring.modelScore - bestScoring2.modelScore < learningMargin) {
					// bestScore matches bestTruth1, but runner up is within the margin
					//Console.println ("Learning from margin")
					// ...update parameters by adding sufficient stats of truth, and subtracting runner-up
					bestScoring.diff.redo
					bestScoring.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
					bestScoring.diff.undo
					bestScoring.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestScoring2.diff.redo
					bestScoring2.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, -learningRate))
					bestScoring2.diff.undo
					bestScoring2.diff.factorsOf[AbstractPerceptronLearning].foreach(f => f.template.increment(f, learningRate))
				}
			} // else println("No true preference.")

			//println("Chosen jump: " + bestScoring.diff)

			bestScoring.diff.redo // TODO consider sampling here instead; or sometimes picking bestTruth1

			//if (random.nextDouble < 0.3) bestTruth1.diff.redo
			//else if (random.nextDouble < 0.5) bestScoring.diff.redo
			//else proposals.sample(p => 1.0).diff.redo

			// Populate and manage the size of the priority queue
			if (useQueue && maxQueueSize > 0) {
				queue ++= bestScoring.diff.factors
				if (queue.size > maxQueueSize) queue.reduceToSize(maxQueueSize)
			}
		}

	}

}


