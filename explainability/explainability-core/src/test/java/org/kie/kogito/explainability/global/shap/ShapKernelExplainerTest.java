/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.explainability.global.shap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.TestUtils;
import org.kie.kogito.explainability.model.Feature;
import org.kie.kogito.explainability.model.FeatureFactory;
import org.kie.kogito.explainability.model.Prediction;
import org.kie.kogito.explainability.model.PredictionInput;
import org.kie.kogito.explainability.model.PredictionOutput;
import org.kie.kogito.explainability.model.PredictionProvider;
import org.kie.kogito.explainability.utils.DataUtils;
import org.kie.kogito.explainability.utils.MatrixUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShapKernelExplainerTest {
    double[][] backgroundRaw = {
            { 1., 2., 3., -4., 5. },
            { 10., 11., 12., -4., 13. },
            { 2., 3, 4., -4., 6. },
    };
    double[][] toExplainRaw = {
            { 5., 6., 7., -4., 8. },
            { 11., 12., 13., -5., 14. },
            { 0., 0, 1., 4., 2. },
    };

    // no variance test case matrices  ===============
    double[][] backgroundNoVariance = {
            { 1., 2., 3. },
            { 1., 2., 3. }
    };

    double[][] toExplainZeroVariance = {
            { 1., 2., 3. },
            { 1., 2., 3. },
    };

    double[][][] zeroVarianceOneOutputSHAP = {
            { { 0., 0., 0. } },
            { { 0., 0., 0. } },
    };

    double[][][] zeroVarianceMultiOutputSHAP = {
            { { 0., 0., 0. }, { 0., 0., 0 } },
            { { 0., 0., 0. }, { 0., 0., 0 } },
    };

    // single variance test case matrices ===============
    double[][] toExplainOneVariance = {
            { 3., 2., 3. },
            { 1., 2., 2. },
    };

    double[][][] oneVarianceOneOutputSHAP = {
            { { 2., 0., 0. } },
            { { 0., 0., -1. } },
    };

    double[][][] oneVarianceMultiOutputSHAP = {
            { { 2., 0., 0. }, { 4., 0., 0. } },
            { { 0., 0., -1. }, { 0., 0., -2 } },
    };

    // multi variance, one output logit test case matrices ===============
    double[][] toExplainLogit = {
            { 0.1, 0.12, 0.14, -0.08, 0.16 },
            { 0.22, 0.24, 0.26, -0.1, 0.38 },
            { -0.1, 0., 0.02, 0.1, 0.04 }
    };

    double[][] backgroundLogit = {
            { 0.02380952, 0.04761905, 0.07142857, -0.0952381, 0.11904762 },
            { 0.23809524, 0.26190476, 0.28571429, -0.0952381, 0.30952381 },
            { 0.04761905, 0.07142857, 0.11904762, -0.0952381, 0.14285714 }
    };

    double[][][] logitSHAP = {
            { { -0.01420862, 0., -0.08377778, 0.06825253, -0.13625127 } },
            { { 0.50970797, 0., 0.44412765, -0.02169177, 0.80832232 } },
            { { Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN } }
    };

    // multiple variance test case matrices ===============
    double[][][] multiVarianceOneOutputSHAP = {
            { { 0.66666667, 0., 0.66666667, 0., 0. } },
            { { 6.66666667, 0., 6.66666667, -1., 6. } },
            { { -4.33333333, 0., -5.33333333, 8., -6. } },
    };

    double[][][] multiVarianceMultiOutputSHAP = {
            { { 0.66666667, 0., 0.66666667, 0., 0. }, { 1.333333333, 0., 1.33333333, 0., 0. } },
            { { 6.66666667, 0., 6.66666667, -1., 6. }, { 13.333333333, 0., 13.333333333, -2., 12. } },
            { { -4.33333333, 0., -5.33333333, 8., -6. }, { -8.6666666667, 0., -10.666666666, 16., -12. } },
    };

    Random shapTestRandom = new Random(0);

    // test helper functions ===========================================================================================
    // create a list of prediction inputs from double matrix
    private List<PredictionInput> createPIFromMatrix(double[][] m) {
        List<PredictionInput> pis = new ArrayList<>();
        int[] shape = MatrixUtils.getShape(m);
        for (int i = 0; i < shape[0]; i++) {
            List<Feature> fs = new ArrayList<>();
            for (int j = 0; j < shape[1]; j++) {
                fs.add(FeatureFactory.newNumericalFeature("f", m[i][j]));
            }
            pis.add(new PredictionInput(fs));
        }
        return pis;
    }

    /*
     * given a specific model, config, background, explanations, and expected shap values,
     * test that the computed shape values match expected shap values
     */
    private void shapTestCase(PredictionProvider model, ShapConfig skConfig,
            double[][] backgroundRaw, double[][] toExplainRaw, double[][][] expected)
            throws InterruptedException, TimeoutException, ExecutionException {

        // establish background data and desired data to explain
        List<PredictionInput> background = createPIFromMatrix(backgroundRaw);
        List<PredictionInput> toExplain = createPIFromMatrix(toExplainRaw);

        //initialize explainer
        ShapKernelExplainer ske = new ShapKernelExplainer(model, skConfig, background, shapTestRandom);
        List<PredictionOutput> predictionOutputs = model.predictAsync(toExplain).get(5, TimeUnit.SECONDS);
        List<Prediction> predictions = DataUtils.getPredictions(toExplain, predictionOutputs);

        // evaluate if the explanations match the expected value
        double[][][] explanations = ske.explainFromPredictions(model, predictions).get(5, TimeUnit.SECONDS);
        for (int i = 0; i < explanations.length; i++) {
            for (int j = 0; j < explanations[0].length; j++) {
                assertArrayEquals(expected[i][j], explanations[i][j], 1e-6);
            }

        }
    }

    // Single output models ============================================================================================
    // test a single output model with no varying features
    @Test
    void testNoVarianceOneOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 100);
        shapTestCase(model, skConfig, backgroundNoVariance, toExplainZeroVariance,
                zeroVarianceOneOutputSHAP);
    }

    // test a single output model with one varying feature
    @Test
    void testOneVarianceOneOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 100);
        shapTestCase(model, skConfig, backgroundNoVariance, toExplainOneVariance,
                oneVarianceOneOutputSHAP);
    }

    // test a single output model with many varying features
    @Test
    void testMultiVarianceOneOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 35);
        shapTestCase(model, skConfig, backgroundRaw, toExplainRaw,
                multiVarianceOneOutputSHAP);
    }

    // test a single output model with many varying features and logit link
    @Test
    void testMultiVarianceOneOutputLogit() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.LOGIT, 100);
        shapTestCase(model, skConfig, backgroundLogit, toExplainLogit,
                logitSHAP);
    }

    // Multi-output models =============================================================================================
    // test a multi-output model with no varying features
    @Test
    void testNoVarianceMultiOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipTwoOutputModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 100);
        shapTestCase(model, skConfig, backgroundNoVariance, toExplainZeroVariance,
                zeroVarianceMultiOutputSHAP);
    }

    // test a multi-output model with one varying feature
    @Test
    void testOneVarianceMultiOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipTwoOutputModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 100);
        shapTestCase(model, skConfig, backgroundNoVariance, toExplainOneVariance,
                oneVarianceMultiOutputSHAP);
    }

    // test a multi-output model with many varying features
    @Test
    void testMultiVarianceMultiOutput() throws InterruptedException, TimeoutException, ExecutionException {
        PredictionProvider model = TestUtils.getSumSkipTwoOutputModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY, 100);
        shapTestCase(model, skConfig, backgroundRaw, toExplainRaw,
                multiVarianceMultiOutputSHAP);
    }

    // Test cases where search space cannot be fully enumerated ========================================================
    @Test
    void testLargeBackground() throws InterruptedException, TimeoutException, ExecutionException {
        // establish background data and desired data to explain
        double[][] largeBackground = new double[100][10];
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 10; j++) {
                largeBackground[i][j] = i / 100. + j;
            }
        }
        double[][] toExplainLargeBackground = {
                { 0, 1., -2., 3.5, -4.1, 5.5, -12., .8, .11, 15. }
        };

        double[][][] expected = {
                { { -0.495, 0., -4.495, 0.005, -8.595, 0.005, -18.495,
                        -6.695, -8.385, 5.505 } }
        };

        List<PredictionInput> background = createPIFromMatrix(largeBackground);
        List<PredictionInput> toExplain = createPIFromMatrix(toExplainLargeBackground);

        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY);

        //initialize explainer
        ShapKernelExplainer ske = new ShapKernelExplainer(model, skConfig, background, shapTestRandom);
        List<PredictionOutput> predictionOutputs = model.predictAsync(toExplain).get();
        List<Prediction> predictions = DataUtils.getPredictions(toExplain, predictionOutputs);

        // evaluate if the explanations match the expected value
        double[][][] explanations = ske.explainFromPredictions(model, predictions).get(5, TimeUnit.SECONDS);

        for (int i = 0; i < explanations.length; i++) {
            for (int j = 0; j < explanations[0].length; j++) {
                assertArrayEquals(expected[i][j], explanations[i][j], 1e-2);
            }
        }
    }

    // Test cases with size errors ========================================================
    @Test
    void testTooLargeBackground() throws InterruptedException, TimeoutException, ExecutionException {
        // establish background data and desired data to explain
        double[][] tooLargeBackground = new double[10][10];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                tooLargeBackground[i][j] = i / 10. + j;
            }
        }
        double[][] toExplainTooSmall = {
                { 0, 1., -2., 3.5 }
        };

        List<PredictionInput> background = createPIFromMatrix(tooLargeBackground);
        List<PredictionInput> toExplain = createPIFromMatrix(toExplainTooSmall);

        PredictionProvider model = TestUtils.getSumSkipModel(1);
        ShapConfig skConfig = new ShapConfig(ShapConfig.LinkType.IDENTITY);

        //initialize explainer
        ShapKernelExplainer ske = new ShapKernelExplainer(model, skConfig, background, shapTestRandom);
        List<PredictionOutput> predictionOutputs = model.predictAsync(toExplain).get(5, TimeUnit.SECONDS);
        List<Prediction> predictions = DataUtils.getPredictions(toExplain, predictionOutputs);

        // evaluate if the explanations match the expected value
        CompletableFuture<double[][][]> explanationsFuture = ske.explainFromPredictions(model, predictions);
        assertThrows(IllegalArgumentException.class, () -> explanationsFuture.get(5, TimeUnit.SECONDS));
    }
}
