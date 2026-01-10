package com.chessgame;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.io.File;

public class ChessModelLoader {

    private MultiLayerNetwork sequentialModel;
    private ComputationGraph functionalModel;
    private boolean isSequential;

    public void loadModel(String h5FilePath) throws Exception {
        File modelFile = new File(h5FilePath);
        
        System.out.println("Attempting to load model from: " + modelFile.getAbsolutePath());

        try {
            // Try loading as a Sequential model first (most common)
            // 'false' means we do not want to enforce training configuration 
            // (since we are only doing inference/runtime use)
            this.sequentialModel = KerasModelImport.importKerasSequentialModelAndWeights(h5FilePath, false);
            this.isSequential = true;
            System.out.println("Success: Loaded as Sequential Model.");
            
        } catch (Exception e) {
            System.out.println("Not a sequential model, trying Functional API...");
            
            // Fallback: Try loading as a Functional API model (ComputationGraph)
            this.functionalModel = KerasModelImport.importKerasModelAndWeights(h5FilePath, false);
            this.isSequential = false;
            System.out.println("Success: Loaded as Functional Model.");
        }
    }

    public int predict(float[] imageData, int height, int width, int channels) {
        // 1. Convert flat float array to ND4J array
        // Shape: [BatchSize, Channels, Height, Width]
        INDArray input = Nd4j.create(imageData).reshape(1, channels, height, width);

        INDArray output;
        
        // 2. Run Inference
        if (isSequential) {
            output = sequentialModel.output(input);
        } else {
            output = functionalModel.outputSingle(input);
        }

        // 3. Get the index of the highest probability (The prediction)
        return Nd4j.argMax(output, 1).getInt(0);
    }
}
