/*
 * Copyright (c) 2015-2020, Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tribuo.classification.libsvm;

import com.oracle.labs.mlrg.olcut.config.Config;
import com.oracle.labs.mlrg.olcut.util.Pair;
import org.tribuo.Dataset;
import org.tribuo.Example;
import org.tribuo.ImmutableFeatureMap;
import org.tribuo.ImmutableOutputInfo;
import org.tribuo.classification.Label;
import org.tribuo.classification.WeightedLabels;
import org.tribuo.common.libsvm.LibSVMModel;
import org.tribuo.common.libsvm.LibSVMTrainer;
import org.tribuo.common.libsvm.SVMParameters;
import org.tribuo.provenance.ModelProvenance;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A trainer for classification models that uses LibSVM.
 * <p>
 * See:
 * <pre>
 * Chang CC, Lin CJ.
 * "LIBSVM: a library for Support Vector Machines"
 * ACM transactions on intelligent systems and technology (TIST), 2011.
 * </pre>
 * for the nu-svc algorithm:
 * <pre>
 * Schölkopf B, Smola A, Williamson R, Bartlett P L.
 * "New support vector algorithms"
 * Neural Computation, 2000, 1207-1245.
 * </pre>
 * and for the original algorithm:
 * <pre>
 * Cortes C, Vapnik V.
 * "Support-Vector Networks"
 * Machine Learning, 1995.
 * </pre>
 */
public class LibSVMClassificationTrainer extends LibSVMTrainer<Label> implements WeightedLabels {
    private static final Logger logger = Logger.getLogger(LibSVMClassificationTrainer.class.getName());

    @Config(description="Use Label specific weights.")
    private Map<String,Float> labelWeights = Collections.emptyMap();

    protected LibSVMClassificationTrainer() {}

    public LibSVMClassificationTrainer(SVMParameters<Label> parameters) {
        super(parameters);
    }

    @Override
    public void postConfig() {
        super.postConfig();
        if (!svmType.isClassification()) {
            throw new IllegalArgumentException("Supplied regression or anomaly detection parameters to a classification SVM.");
        }
    }

    @Override
    protected LibSVMModel<Label> createModel(ModelProvenance provenance, ImmutableFeatureMap featureIDMap, ImmutableOutputInfo<Label> outputIDInfo, List<svm_model> models) {
        return new LibSVMClassificationModel("svm-classification-model", provenance, featureIDMap, outputIDInfo, models);
    }

    @Override
    protected List<svm_model> trainModels(svm_parameter curParams, int numFeatures, svm_node[][] features, double[][] outputs) {
        svm_problem problem = new svm_problem();
        problem.l = outputs[0].length;
        problem.x = features;
        problem.y = outputs[0];
        if (parameters.gamma == 0) {
            parameters.gamma = 1.0 / numFeatures;
        }
        String checkString = svm.svm_check_parameter(problem, curParams);
        if(checkString != null) {
            throw new IllegalArgumentException("Error checking SVM parameters: " + checkString);
        }
        return Collections.singletonList(svm.svm_train(problem, curParams));
    }

    @Override
    protected Pair<svm_node[][], double[][]> extractData(Dataset<Label> data, ImmutableOutputInfo<Label> outputInfo, ImmutableFeatureMap featureMap) {
        double[][] ys = new double[1][data.size()];
        svm_node[][] xs = new svm_node[data.size()][];
        List<svm_node> buffer = new ArrayList<>();
        int i = 0;
        for (Example<Label> example : data) {
            ys[0][i] = outputInfo.getID(example.getOutput());
            xs[i] = exampleToNodes(example, featureMap, buffer);
            i++;
        }
        return new Pair<>(xs,ys);
    }

    @Override
    protected svm_parameter setupParameters(ImmutableOutputInfo<Label> outputIDInfo) {
        svm_parameter curParams;
        if (!labelWeights.isEmpty()) {
            curParams = (svm_parameter) parameters.clone();
            double[] weights = new double[outputIDInfo.size()];
            int[] indices = new int[outputIDInfo.size()];
            int i = 0;
            for (Pair<Integer,Label> label : outputIDInfo) {
                String labelName = label.getB().getLabel();
                Float weight = labelWeights.get(labelName);
                indices[i] = label.getA();
                if (weight != null) {
                    weights[i] = weight;
                } else {
                    weights[i] = 1.0f;
                }
                i++;
            }
            curParams.nr_weight = weights.length;
            curParams.weight = weights;
            curParams.weight_label = indices;
            //logger.info("Weights = " + Arrays.toString(weights) + ", labels = " + Arrays.toString(indices) + ", outputIDInfo = " + outputIDInfo);
        } else {
            curParams = parameters;
        }
        return curParams;
    }

    @Override
    public void setLabelWeights(Map<Label,Float> weights) {
        labelWeights = new HashMap<>();
        for (Map.Entry<Label,Float> e : weights.entrySet()) {
            labelWeights.put(e.getKey().getLabel(),e.getValue());
        }
    }
}
