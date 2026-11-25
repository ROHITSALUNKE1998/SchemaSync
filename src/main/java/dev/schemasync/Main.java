package dev.schemasync;

import java.nio.file.*;
import java.io.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Main.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (var context = app.run(args)) { }
    }

    public void run(String... args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java -jar schemasync.jar DOCUMENTS.jsonl MAPPING.json");
        try (var rows = Files.newBufferedReader(Path.of(args[0]))) {
            var result = Audit.compare(rows, Audit.JSON.readTree(Files.readString(Path.of(args[1]))));
            System.out.println(Audit.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }
}
