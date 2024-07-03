/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package ai.djl.examples.training.util;

import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.util.JsonUtils;

import com.google.gson.reflect.TypeToken;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.lang.reflect.Type;
import java.util.Map;

public class Arguments {

    protected int epoch;
    protected int batchSize;
    protected int maxGpus;
    protected boolean preTrained;
    protected String outputDir;
    protected long limit;
    protected String modelDir;
    protected Map<String, String> criteria;
    protected String engine;

    protected void initialize() {
        epoch = 2;
        outputDir = "build/model";
        limit = Long.MAX_VALUE;
        modelDir = null;
    }

    protected void setCmd(CommandLine cmd) {
        if (cmd.hasOption("epoch")) {
            epoch = Integer.parseInt(cmd.getOptionValue("epoch"));
        }
        if (cmd.hasOption("max-gpus")) {
            maxGpus = Integer.parseInt(cmd.getOptionValue("max-gpus"));
        }
        if (cmd.hasOption("batch-size")) {
            batchSize = Integer.parseInt(cmd.getOptionValue("batch-size"));
        } else {
            batchSize = maxGpus > 0 ? 32 * maxGpus : 32;
        }
        preTrained = cmd.hasOption("pre-trained");

        if (cmd.hasOption("output-dir")) {
            outputDir = cmd.getOptionValue("output-dir");
        }
        if (cmd.hasOption("max-batches")) {
            limit = Long.parseLong(cmd.getOptionValue("max-batches")) * batchSize;
        }
        if (cmd.hasOption("model-dir")) {
            modelDir = cmd.getOptionValue("model-dir");
        }
        if (cmd.hasOption("criteria")) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            criteria = JsonUtils.GSON.fromJson(cmd.getOptionValue("criteria"), type);
        }
        if (cmd.hasOption("engine")) {
            engine = cmd.getOptionValue("engine");
        } else {
            engine = "PyTorch";
        }
    }

    public Arguments parseArgs(String[] args) {
        initialize();
        Options options = getOptions();
        try {
            DefaultParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args, null, false);
            if (cmd.hasOption("help")) {
                printHelp("./gradlew run --args='[OPTIONS]'", options);
                return null;
            }
            setCmd(cmd);
            return this;
        } catch (ParseException e) {
            printHelp("./gradlew run --args='[OPTIONS]'", options);
        }
        return null;
    }

    public Options getOptions() {
        Options options = new Options();
        options.addOption(
                Option.builder("h").longOpt("help").hasArg(false).desc("Print this help.").build());
        options.addOption(
                Option.builder("e")
                        .longOpt("epoch")
                        .hasArg()
                        .argName("EPOCH")
                        .desc("Numbers of epochs user would like to run")
                        .build());
        options.addOption(
                Option.builder("b")
                        .longOpt("batch-size")
                        .hasArg()
                        .argName("BATCH-SIZE")
                        .desc("The batch size of the training data.")
                        .build());
        options.addOption(
                Option.builder("g")
                        .longOpt("max-gpus")
                        .hasArg()
                        .argName("MAXGPUS")
                        .desc("Max number of GPUs to use for training")
                        .build());
        options.addOption(
                Option.builder("p")
                        .longOpt("pre-trained")
                        .argName("PRE-TRAINED")
                        .desc("Use pre-trained weights")
                        .build());
        options.addOption(
                Option.builder("o")
                        .longOpt("output-dir")
                        .hasArg()
                        .argName("OUTPUT-DIR")
                        .desc("Use output to determine directory to save your model parameters")
                        .build());
        options.addOption(
                Option.builder("m")
                        .longOpt("max-batches")
                        .hasArg()
                        .argName("max-batches")
                        .desc(
                                "Limit each epoch to a fixed number of iterations to test the"
                                        + " training script")
                        .build());
        options.addOption(
                Option.builder("d")
                        .longOpt("model-dir")
                        .hasArg()
                        .argName("MODEL-DIR")
                        .desc("pre-trained model file directory")
                        .build());
        options.addOption(
                Option.builder("r")
                        .longOpt("criteria")
                        .hasArg()
                        .argName("CRITERIA")
                        .desc("The criteria used for the model.")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("engine")
                        .hasArg()
                        .argName("ENGINE")
                        .desc("The engine for the model.")
                        .build());
        return options;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getEpoch() {
        return epoch;
    }

    public Device[] getMaxGpus() {
        return Engine.getEngine(engine).getDevices(maxGpus);
    }

    public boolean isPreTrained() {
        return preTrained;
    }

    public String getModelDir() {
        return modelDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public long getLimit() {
        return limit;
    }

    public Map<String, String> getCriteria() {
        return criteria;
    }

    public String getEngine() {
        return engine;
    }

    private void printHelp(String msg, Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setLeftPadding(1);
        formatter.setWidth(120);
        formatter.printHelp(msg, options);
    }
}
