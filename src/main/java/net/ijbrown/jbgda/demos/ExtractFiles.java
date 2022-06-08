package net.ijbrown.jbgda.demos;

import net.ijbrown.jbgda.loaders.*;
import org.tinylog.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.FileVisitResult.CONTINUE;

/*
    Unpacks the LMP and GOB files and parses the files inside them.
    Writes the results to a sibling directory.
 */
public class ExtractFiles
{
    public static void main(String[] args) throws IOException {

        // Specify here if you want to extract all the GOB and LMP files (you only need to do this first time).
        // You can also specify a pattern which, if set, will restrict asset conversion to only files that contain
        // that pattern (useful for debugging).

        new ExtractFiles().doExtract(GameType.DARK_ALLIANCE, false, "hud_statusbar");
    }

    private void doExtract(GameType gameType, boolean extractLmps, String pattern) throws IOException {
        var config = new Config(gameType);
        var gameDataPath = FileSystems.getDefault().getPath(config.getDataDir());

        var extractedPath = FileSystems.getDefault().getPath(gameDataPath.toString() + "_EXTRACTED");

        Files.createDirectories(extractedPath);

        Logger.info("Extracting to {}", extractedPath);
        if (extractLmps) {
            extractGobs(gameDataPath, extractedPath, gameType);
            extractLmps(gameDataPath, extractedPath, gameType);
        }
        convertTexFiles(extractedPath, gameType, pattern);
    }

    public static class FileFinder extends SimpleFileVisitor<Path>
    {
        private final String ext;

        public List<Path> found = new ArrayList<>();

        FileFinder(String ext)
        {
            this.ext = ext;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            if (attr.isRegularFile()) {
                if (file.toString().endsWith(ext)){
                    found.add(file);
                }
            }
            return CONTINUE;
        }
    }

    private void convertTexFiles(Path extractedPath, GameType gameType, String pattern) throws IOException {
        TexDecode decoder = new TexDecode();

        var texFileFinder = new FileFinder(".tex");
        Files.walkFileTree(extractedPath, texFileFinder);

        Logger.info("found {} tex files", texFileFinder.found.size());
        for (var texFile : texFileFinder.found){
            if (pattern == null || pattern.isEmpty() || texFile.toString().contains(pattern)) {
                Logger.info("Converting {}", texFile.toString());
                try {
                    decoder.extract(texFile);
                } catch (RuntimeException e) {
                    Logger.info("Failed to convert {}", texFile.toString());
                }
                LogTexFile(texFile);
            }
        }
    }

    private void LogTexFile(Path texPath) throws IOException {
        var texLogger = new TexLogger();
        var texFilename = texPath.getFileName().toString();
        var outDir = texPath.getParent();
        var txtFilename = texFilename.replace(".tex", "_tex.txt");
        var outPath = outDir.resolve(txtFilename);
        try (var writer = new FileWriter(outPath.toFile())){
            texLogger.log(texPath, writer);
        }
    }

    private void extractLmps(Path gameDataPath, Path extractedPath, GameType gameType) throws IOException {
        var extractor = new LmpExtractor(gameType);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameDataPath, "*.LMP")) {
            for (Path entry: stream) {
                var lmpFilename = entry.getFileName();
                Logger.info("Extracting {}", lmpFilename);
                var outDirname = lmpFilename.toString().replace('.', '_');
                var outPath = extractedPath.resolve(outDirname);
                Files.createDirectories(outPath);
                extractor.extractAll(lmpFilename, entry.toAbsolutePath(), outPath);
            }
        }
    }

    private void extractGobs(Path gameDataPath, Path extractedPath, GameType gameType) throws IOException {
        var extractor = new GobExtractor(gameType);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameDataPath, "*.GOB")) {
            for (Path entry: stream) {
                var gobFilename = entry.getFileName();
                Logger.info("Extracting {}", gobFilename);
                var outDirname = gobFilename.toString().replace('.', '_');
                var outPath = extractedPath.resolve(outDirname);
                Files.createDirectories(outPath);
                extractor.extract(gobFilename, entry.toAbsolutePath(), outPath);
            }
        }
    }
}
