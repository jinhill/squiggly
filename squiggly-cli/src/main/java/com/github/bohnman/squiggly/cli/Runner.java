package com.github.bohnman.squiggly.cli;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.bohnman.core.io.OutputStreamWrapper;
import com.github.bohnman.core.lang.CoreStrings;
import com.github.bohnman.squiggly.cli.config.Config;
import com.github.bohnman.squiggly.jackson.Squiggly;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;

public class Runner implements Runnable {

    private final ObjectMapper mapper;
    private final Config config;
    private final Squiggly squiggly;


    public Runner(String... args) {
        this.config = new Config(args);
        this.mapper = buildObjectMapper();
        this.squiggly = buildSquiggly();
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        if (!config.isCompact()) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        if (config.isSortKeys()) {
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        }

        return mapper;
    }

    private Squiggly buildSquiggly() {
        Squiggly.Builder builder = Squiggly.builder();
        config.getVariables().forEach(builder::variable);
        return builder.build();
    }

    @Override
    public final void run() {
        try {
            doRun();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void doRun() throws Throwable {
        if (config.isNullInput()) {
            handle(mapper.getNodeFactory().nullNode());
        } else if (config.getFiles().isEmpty()) {
            read(System.in);
        } else {
            for (String file : config.getFiles()) {
                try (FileInputStream in = new FileInputStream(file)) {
                    read(in);
                }
            }
        }
    }

    private void read(InputStream in) {
        try (Reader reader = readerFor(in)) {
            JsonParser parser = mapper.getFactory().createParser(reader);

            while (!parser.isClosed()) {
                JsonNode tree = parser.readValueAsTree();

                if (tree == null) {
                    continue;
                }

                handle(tree);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void handle(JsonNode tree) throws IOException {
        JsonNode result = squiggly.apply(tree, config.getFilter());
        PrintStream out = System.out;

        if (result.isTextual() && config.isRawOutput()) {
            out.println(result.asText());
        } else {
            write(result, out);
            out.println();
        }
    }

    private void write(JsonNode tree, PrintStream out) throws IOException {
        char indentCh = config.isTab() ? '\t' : ' ';
        String indent = CoreStrings.repeat(indentCh, config.getIndent());
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter(indent, DefaultIndenter.SYS_LF);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        mapper.writer(printer).writeValue(new PreventCloseOutputStream(out), tree);
    }

    private Reader readerFor(InputStream in) {
        return new BufferedReader(new InputStreamReader(in));
    }

    private class PreventCloseOutputStream extends OutputStreamWrapper {
        public PreventCloseOutputStream(OutputStream wrapped) {
            super(wrapped);
        }

        @Override
        public void close() {
        }
    }


    public static void main(String... args) {
        new Runner(args).run();
    }
}
