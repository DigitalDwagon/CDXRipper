package dev.digitaldragon;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.github.luben.zstd.ZstdInputStream;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class Main {
    //private static File outputFile = new File("output.txt");
    private static File outputFile = null;
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
     public static void main(String[] args) throws InterruptedException {
        /*if (args.length != 1) {
            System.err.println("Usage: java GzipReader <url>");
            System.exit(1);
        }

        String url = args[0];
        */
        Args parsed = new Args();
         JCommander.newBuilder()
                 .addObject(parsed)
                 .build()
                 //.parse(new String[]{"links.txt", "-o", "output.txt"});
                 .parse(args);


        outputFile = new File(parsed.getOutputFile());


        //String url = "https://data.commoncrawl.org/cc-index/collections/CC-MAIN-2023-40/indexes/cdx-00000.gz";

        try {
            List<String> urls = Files.readAllLines(Paths.get(parsed.getUrlList()));

            /*List<String> urls = new ArrayList<>();
            urls.add("https://archive.org/download/archiveteam_archivebot_go_20231121172054_289d7ef6/27.tumblr.com-inf-20230809-001840-cywaz-03288.warc.os.cdx.gz");
            //*/

            for (String url : urls) {
                System.out.println("Downloading " + url);
                getInputStream(url);
                System.out.println("Done downloading " + url + ". May still be writing the file.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
         System.out.println(outputFile.getAbsolutePath());
        System.out.println("Finished downloading data! Completing pending writes...");
        executor.shutdown();

    }

    private static void getInputStream(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        InputStream inputStream;
        if (urlString.endsWith(".gz")) {
            inputStream = new GZIPInputStream(urlConnection.getInputStream());
        } else if (urlString.endsWith(".zst")) {
            inputStream = new ZstdInputStream(urlConnection.getInputStream());
        } else {
            inputStream = urlConnection.getInputStream();
        }
        CDXType cdxType;
        if (urlString.contains("data.commoncrawl.org")) {
            cdxType = CDXType.COMMONCRAWL;
        } else {
            cdxType = CDXType.INTERNETARCHIVE;
        }

        downloadCdxContent(inputStream, cdxType);
    }

    private static void downloadCdxContent(InputStream inputStream, CDXType cdxType) throws IOException {
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {

            String line;
            int bytesDownloaded = 0;
            while ((line = reader.readLine()) != null) {
                bytesDownloaded += line.getBytes(StandardCharsets.UTF_8).length; // Count downloaded bytes
                System.out.printf("Download progress: %s bytes (after decompression)\r", bytesDownloaded); // Show progress

                parseCdxLine(line, cdxType);
            }
        }
    }

    private static final List<String> lines = new ArrayList<>();
    private static List<String> header = null;
    private static void parseCdxLine(String line, CDXType cdxType) {
        if (line.startsWith(" CDX ")) {
            //System.out.println("aaa!");
            String[] split = line.replace("CDX", "").trim().split(" ");
            header = Arrays.asList(split);
        } else {
            lines.add(line);
        }
        if (!(lines.size() >= 10000)) {
            return;
        }

        List<String> linesCopy = new ArrayList<>(lines);
        lines.clear();
        executor.execute(() -> {
            List<String> writeLines = new ArrayList<>();

            if (cdxType == CDXType.INTERNETARCHIVE || cdxType == CDXType.IIPCSTANDARD) {
                //read CDX header to find what column "a" is in.
                //first line is header, looks like " CDX A b e a m s c k r V v D d g M n"
                int aColumn = header.indexOf("a");
                if (aColumn == -1) {
                    System.err.println("Error: Could not find column \"a\" in CDX header.");
                    return;
                }

                for (String line1 : linesCopy) {
                    String[] split = line1.split(" ");
                    writeLines.add(split[aColumn]);
                }
            }

            if (cdxType == CDXType.COMMONCRAWL) {
                for (String line1 : linesCopy) {
                    JSONObject jsonObject = new JSONObject(line1.split(" ", 3)[2]);
                    writeLines.add(jsonObject.getString("url"));
                }
            }

            writeLinesToFile(outputFile, writeLines);
        });
        lines.clear();
    }

    private static synchronized void writeLineToFile(File file, String line) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.append(line);
            writer.append('\n');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static synchronized void writeLinesToFile(File file, List<String> lines) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            for (String s : lines) {
                writer.append(s);
                writer.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}