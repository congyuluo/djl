/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.pytorch.engine;

import ai.djl.BaseModel;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.ndarray.types.DataType;
import ai.djl.nn.Parameter;
import ai.djl.nn.Parameter.Type;
import ai.djl.pytorch.jni.JniUtils;
import ai.djl.training.Trainer;
import ai.djl.training.TrainingConfig;
import ai.djl.training.initializer.Initializer;
import ai.djl.util.Pair;
import ai.djl.util.PairList;
import ai.djl.util.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code PtModel} is the PyTorch implementation of {@link Model}.
 *
 * <p>PtModel contains all the methods in Model to load and process a model. In addition, it
 * provides PyTorch Specific functionality
 */
public class PtModel extends BaseModel {

    /**
     * Constructs a new Model on a given device.
     *
     * @param name the model name
     * @param device the device the model should be located on
     */
    PtModel(String name, Device device) {
        super(name);
        manager = PtNDManager.getSystemManager().newSubManager(device);
        manager.setName("ptModel");
        dataType = DataType.FLOAT32;
    }

    /** {@inheritDoc} */
    @Override
    public void load(Path modelPath, String prefix, Map<String, ?> options)
            throws IOException, MalformedModelException {
        setModelDir(modelPath);
        wasLoaded = true;

        Path modelFile;
        if (prefix != null) {
            modelFile = findModelFile(prefix);
        } else {
            // search for .pt file with modelName, folder name or "model.pt"
            modelFile = findModelFile(modelName, modelDir.toFile().getName(), "model.pt");
            prefix = modelName;
        }

        if (block == null) {
            if (modelFile == null) {
                String fileName = prefix.endsWith(".pt") ? prefix : prefix + ".pt";
                throw new FileNotFoundException(fileName + " file not found in: " + modelDir);
            }
            String[] extraFileKeys = Utils.EMPTY_ARRAY;
            String[] extraFileValues = Utils.EMPTY_ARRAY;
            boolean mapLocation = false;
            boolean trainParam = false;
            // load jit extra files
            if (options != null) {
                if (options.containsKey("extraFiles")) {
                    extraFileKeys = ((String) options.get("extraFiles")).split(",");
                    extraFileValues = new String[extraFileKeys.length];
                }
                trainParam = Boolean.parseBoolean((String) options.get("trainParam"));
                mapLocation = Boolean.parseBoolean((String) options.get("mapLocation"));
            }
            block =
                    JniUtils.loadModule(
                            (PtNDManager) manager,
                            modelFile,
                            mapLocation,
                            extraFileKeys,
                            extraFileValues,
                            trainParam);
            for (int i = 0; i < extraFileKeys.length; i++) {
                properties.put(extraFileKeys[i], extraFileValues[i]);
            }

            /*
             * By default, the parameters are frozen, since the previous version before adding this
             * trainParam, they were frozen due to the setting JITCallGuard guard, which disables
             * autograd. Also, the pretrained parameters usually should not be updated too much. It
             * is safe to freeze it. Users may unfreeze it and set their learning rate small.
             */
            block.freezeParameters(!trainParam);
        } else {
            loadBlock(prefix, options);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void load(InputStream modelStream, Map<String, ?> options)
            throws IOException, MalformedModelException {
        boolean mapLocation = false;
        if (options != null) {
            mapLocation = Boolean.parseBoolean((String) options.get("mapLocation"));
        }
        load(modelStream, mapLocation);
    }

    /**
     * Load PyTorch model from {@link InputStream}.
     *
     * @param modelStream the stream of the model file
     * @param mapLocation force load to specified device if true
     * @throws IOException model loading error
     * @throws MalformedModelException if model file is corrupted
     */
    public void load(InputStream modelStream, boolean mapLocation)
            throws IOException, MalformedModelException {
        wasLoaded = true;
        if (block == null) {
            modelDir = Files.createTempDirectory("pt-model");
            modelDir.toFile().deleteOnExit();
            block = JniUtils.loadModule((PtNDManager) manager, modelStream, mapLocation, false);

            /*
             * By default, the parameters are frozen, since the previous version before adding this
             * trainParam, they were frozen due to the setting JITCallGuard guard, which disables
             * autograd. Also, the pretrained parameters usually should not be updated too much. It
             * is safe to freeze it. Users may unfreeze it and set their learning rate small.
             */
            block.freezeParameters(true);
        } else {
            readParameters(modelStream, Collections.emptyMap());
        }
    }

    private Path findModelFile(String... prefixes) {
        if (Files.isRegularFile(modelDir)) {
            Path file = modelDir;
            modelDir = modelDir.getParent();
            String fileName = file.toFile().getName();
            if (fileName.endsWith(".pt")) {
                modelName = fileName.substring(0, fileName.length() - 3);
            } else {
                modelName = fileName;
            }
            return file;
        }
        for (String prefix : prefixes) {
            Path modelFile = modelDir.resolve(prefix);
            if (Files.isRegularFile(modelFile)) {
                return modelFile;
            }
            if (!prefix.endsWith(".pt")) {
                modelFile = modelDir.resolve(prefix + ".pt");
                if (Files.isRegularFile(modelFile)) {
                    return modelFile;
                }
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Trainer newTrainer(TrainingConfig trainingConfig) {
        PairList<Initializer, Predicate<Parameter>> initializer = trainingConfig.getInitializers();
        if (block == null) {
            throw new IllegalStateException(
                    "You must set a block for the model before creating a new trainer");
        }
        if (wasLoaded) {
            // Unfreeze parameters if training directly
            block.freezeParameters(
                    false,
                    param -> param.getType() != Type.RUNNING_MEAN && param.getType() != Type.RUNNING_VAR);
        }
        for (Pair<Initializer, Predicate<Parameter>> pair : initializer) {
            if (pair.getKey() != null && pair.getValue() != null) {
                block.setInitializer(pair.getKey(), pair.getValue());
            }
        }

        return new Trainer(this, trainingConfig);
    }

    /** {@inheritDoc} */
    @Override
    public String[] getArtifactNames() {
        try (Stream<Path> stream = Files.walk(modelDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            List<String> ret = new ArrayList<>(files.size());
            for (Path path : files) {
                String fileName = path.toFile().getName();
                if (fileName.endsWith(".pt")) {
                    // ignore model files.
                    continue;
                }
                Path relative = modelDir.relativize(path);
                ret.add(relative.toString());
            }
            return ret.toArray(Utils.EMPTY_ARRAY);
        } catch (IOException e) {
            throw new AssertionError("Failed list files", e);
        }
    }
}
