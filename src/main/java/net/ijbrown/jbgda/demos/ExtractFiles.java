package net.ijbrown.jbgda.demos;

import net.ijbrown.jbgda.exporters.Gltf;
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

        //new ExtractFiles().doExtract(GameType.DARK_ALLIANCE, false, "objects");
        //new ExtractFiles().doExtract(GameType.JUSTICE_LEAGUE_HEROES, true, "");
        new ExtractFiles().doExtract(GameType.CHAMPIONS_RTA, false, "ant");
    }

    public void doExtract(GameType gameType, boolean extractLmps, String pattern) throws IOException {
        var config = new Config(gameType);
        var gameDataPath = FileSystems.getDefault().getPath(config.getDataDir());
        Logger.info("Reading game data from {}", gameDataPath);

        var extractedPath = FileSystems.getDefault().getPath(gameDataPath + "_EXTRACTED");
        Logger.info("Extracting to {}", extractedPath);

        Files.createDirectories(extractedPath);

        if (extractLmps) {
            extractGobs(gameDataPath, extractedPath, gameType);
            extractLmps(gameDataPath, extractedPath, gameType);
            extractHDRDATArchives(gameDataPath, extractedPath, gameType);
        }
        convertTexFiles(extractedPath, gameType, pattern);
        convertVifFiles(extractedPath, gameType, pattern);
        convertScriptFiles(extractedPath, gameType, pattern);
        convertObFiles(extractedPath, gameType, pattern);
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

    private void convertVifFiles(Path extractedPath, GameType gameType, String pattern) throws IOException {
        VifDecode decoder = new VifDecode();

        var fileFinder = new FileFinder(".vif");
        Files.walkFileTree(extractedPath, fileFinder);

        Logger.info("found {} vif files", fileFinder.found.size());
        for (var file : fileFinder.found){
            if (pattern == null || pattern.isEmpty() || file.toString().contains(pattern)) {
                Logger.debug("Converting {}", file.toString());
                try {
                    var vifFilename = file.getFileName().toString();
                    var outDir = file.getParent();

                    // Find anm files in the same directory
                    var anmFinder = new FileFinder(".anm");
                    Files.walkFileTree(outDir, anmFinder);

                    byte[] fileData = Files.readAllBytes(file);
                    List<VifDecode.Mesh> meshList = decoder.decode(fileData, 0);

                    int texW=0;
                    int texH=0;
                    String texName=vifFilename.replace(".vif", ".tex");
                    String pngName=vifFilename.replace(".vif", ".png");

                    var texPath = outDir.resolve(texName);
                    if (Files.exists(texPath)){
                        byte[] texData = Files.readAllBytes(texPath);
                        texW = DataUtil.getLEShort(texData, 0);
                        texH = DataUtil.getLEShort(texData, 2);
                    }

                    List<AnmData> anmList = new ArrayList<>();
                    for(var anmPath : anmFinder.found){
                        byte[] anmFileData = Files.readAllBytes(anmPath);
                        AnmDecoder anmDecoder = new AnmDecoder();
                        var anmData = anmDecoder.decode(gameType, anmFileData, 0, anmFileData.length);
                        anmData.name = anmPath.getFileName().toString();
                        anmList.add(anmData);
                    }

                    var gltf = new Gltf(meshList, pngName, texW, texH, anmList);

                    var gltfFilename = vifFilename.replace(".vif", "_vif.gltf");
                    var outPath = outDir.resolve(gltfFilename);
                    gltf.write(outPath);
                } catch (RuntimeException e) {
                    Logger.error(e, "Failed to convert {}", file.toString());
                }
            }
        }
    }

    private void convertTexFiles(Path extractedPath, GameType gameType, String pattern) throws IOException {
        TexDecode decoder = new TexDecode();

        var texFileFinder = new FileFinder(".tex");
        Files.walkFileTree(extractedPath, texFileFinder);

        Logger.info("found {} tex files", texFileFinder.found.size());
        for (var texFile : texFileFinder.found){
            if (pattern == null || pattern.isEmpty() || texFile.toString().contains(pattern)) {
                Logger.debug("Converting {}", texFile.toString());
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

    private void convertObFiles(Path extractedPath, GameType gameType, String pattern) throws IOException {
        var fileFinder = new FileFinder(".ob");
        Files.walkFileTree(extractedPath, fileFinder);

        Logger.info("found {} ob files", fileFinder.found.size());
        for (var file : fileFinder.found){
            if (pattern == null || pattern.isEmpty() || file.toString().contains(pattern)) {
                Logger.debug("Converting {}", file.toString());
                logObFile(file);
            }
        }
    }

    private void logObFile(Path path) throws IOException {
        var filename = path.getFileName().toString();
        var outDir = path.getParent();
        var txtFilename = filename.replace(".ob", "_ob.txt");
        var outPath = outDir.resolve(txtFilename);

        byte[] obData = Files.readAllBytes(path);
        var out =  ObLogger.log(obData);
        Files.writeString(outPath, out);
    }

    private void convertScriptFiles(Path extractedPath, GameType gameType, String pattern) throws IOException {
        ScriptDecode decoder = new ScriptDecode();

        var fileFinder = new FileFinder(".scr");
        Files.walkFileTree(extractedPath, fileFinder);

        Logger.info("found {} scr files", fileFinder.found.size());
        for (var file : fileFinder.found){
            if (pattern == null || pattern.isEmpty() || file.toString().contains(pattern)) {
                Logger.debug("Converting {}", file.toString());
                try {
                    decoder.read(file);
                    String out = decoder.disassemble();

                    var outDir = file.getParent();
                    var filename = file.getFileName().toString();
                    var txtFilename = filename.replace(".scr", "_scr.txt");
                    var outPath = outDir.resolve(txtFilename);

                    Files.writeString(outPath, out);

                } catch (RuntimeException e) {
                    Logger.info("Failed to convert {}", file.toString());
                }
            }
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

    private void extractHDRDATArchives(Path gameDataPath, Path extractedPath, GameType gameType) throws IOException {
        var extractor = new HdrDatExtractor(gameType);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameDataPath, "*.HDR")) {
            for (Path hdrPath: stream) {
                var hdrFilename = hdrPath.getFileName().toString();
                Logger.info("Extracting {}", hdrFilename);
                var outDirname = hdrFilename.replace('.', '_');
                var outPath = extractedPath.resolve(outDirname);
                Files.createDirectories(outPath);

                var baseFilename = hdrFilename.split("\\.")[0];
                var datFilename = baseFilename + ".DAT";
                var datPath = hdrPath.resolveSibling(datFilename);

                extractor.extract(baseFilename, hdrPath.toAbsolutePath(), datPath.toAbsolutePath(), outPath);
            }
        }

    }

}
