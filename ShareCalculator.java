import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class ShareCalculator {
    static String INSTANCE_URL = null;
    static String ACCESS_TOKEN = null;
    static final String API_VERSION = "v64.0";
    static int BATCH_SIZE = 50;
    static int THREAD_COUNT = 10;
    static int SOQL_LIMIT = 10000;
    static String SOQL = null;
    static String OBJECT_TYPE = "Case";
    static List<String> OBJECT_TYPES = null;
    static String CSV_FILE = null;
    static String PROPERTIES_FILE = "ShareCalculator.properties";
    static final int THREAD_TIMEOUT_MS = 100;

    static long lastBatchCount = 0;

    public static void main(String[] args) throws Exception {
        Properties props = loadProperties();
        applyPropertiesAndArgs(props, args);

        if (INSTANCE_URL == null || ACCESS_TOKEN == null) {
            System.err.println("Usage: java ShareCalculator [--instanceUrl <url>] [--accessToken <token>] [--batchSize N] [--threadCount N] [--soqlLimit N] [--soql <query>] [--csvFile <path>]\nParameters can also be set in ShareCalculator.properties");
            System.exit(1);
        }

        if (CSV_FILE != null && SOQL != null) {
            System.err.println("Error: Cannot specify both --csvFile and --soql. Choose one input source.");
            System.exit(1);
        }

        if (CSV_FILE == null && SOQL == null) {
            System.err.println("Error: Must specify either --csvFile or --soql as input source.");
            System.exit(1);
        }

        if (SOQL != null) {
            SOQL += " LIMIT " + SOQL_LIMIT;
        }

        System.out.println("Using batch size: " + BATCH_SIZE + ", thread count: " + THREAD_COUNT + ", SOQL limit: " + SOQL_LIMIT);
        System.out.println("Processing object types: " + OBJECT_TYPES);
        System.out.println("SOQL: " + SOQL);

        long startTime = System.currentTimeMillis();

        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdir();
        }
        File tempIdFile = new File(tempDir, "share_calc_ids_" + System.currentTimeMillis() + ".txt");
        tempIdFile.deleteOnExit();
        System.out.println("Using temp file: " + tempIdFile.getAbsolutePath());

        long totalIds;
        if (CSV_FILE != null) {
            System.out.println("Loading IDs from CSV file: " + CSV_FILE);
            totalIds = streamIdsToFile(loadIdsFromCsvStream(CSV_FILE), tempIdFile);
            System.out.println("Streamed " + totalIds + " IDs from CSV file to temp file.");
        } else {
            String jobId = submitBulkQuery(SOQL);
            System.out.println("Submitted job: " + jobId);

            waitForJobCompletion(jobId);
            System.out.println("Job completed.");

            totalIds = streamBulkQueryResultsToFile(jobId, tempIdFile);
            System.out.println("Streamed " + totalIds + " IDs from bulk query to temp file.");
        }

        if (totalIds == 0) {
            System.out.println("No IDs found.");
            tempIdFile.delete();
            return;
        }

        // Process each object type
        for (String objectType : OBJECT_TYPES) {
            OBJECT_TYPE = objectType;
            System.out.println("\n===== Processing object type: " + OBJECT_TYPE + " =====");
            processObjectType(tempIdFile, totalIds, startTime);
        }

        System.out.println("\nAll object types processed!");
        long endTime = System.currentTimeMillis();
        System.out.printf("Total run time: %.2f seconds\n", (endTime - startTime) / 1000.0);
    }

    static void processObjectType(File tempIdFile, long totalIds, long startTime) throws Exception {
        int totalBatches = (int) Math.ceil(totalIds / (double) BATCH_SIZE);
        System.out.println("Total IDs: " + totalIds + ", Total batches: " + totalBatches);

        final List<Long> batchTimes = Collections.synchronizedList(new ArrayList<>());
        final int[] completedBatches = {0};
        final int[] lastLoggedPercent = {0};
        final long[] lastLoggedTime = {System.currentTimeMillis()};

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try (BufferedReader reader = new BufferedReader(new FileReader(tempIdFile))) {
            List<String> batch = new ArrayList<>();
            String id;
            while ((id = reader.readLine()) != null) {
                batch.add(id.trim());
                if (batch.size() == BATCH_SIZE) {
                    final List<String> currentBatch = new ArrayList<>(batch);
                    executor.submit(() -> processBatch(currentBatch, completedBatches, batchTimes, totalBatches, lastLoggedPercent, lastLoggedTime, startTime));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                final List<String> currentBatch = new ArrayList<>(batch);
                executor.submit(() -> processBatch(currentBatch, completedBatches, batchTimes, totalBatches, lastLoggedPercent, lastLoggedTime, startTime));
            }
        }

        executor.shutdown();
        executor.awaitTermination(10L, TimeUnit.DAYS);

        System.out.println("Batches for " + OBJECT_TYPE + " processed!");
        double avgBatchTime = batchTimes.isEmpty() ? 0 : (batchTimes.stream().mapToLong(Long::longValue).sum() / (double) batchTimes.size());
        System.out.printf("Average batch time: %.2f seconds\n", avgBatchTime / 1000.0);
    }

    static Properties loadProperties() {
        Properties props = new Properties();
        try {
            File f = new File(PROPERTIES_FILE);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    props.load(fis);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load properties file: " + e.getMessage());
        }
        return props;
    }

    static void applyPropertiesAndArgs(Properties props, String[] args) {
        INSTANCE_URL = props.getProperty("instanceUrl", INSTANCE_URL);
        ACCESS_TOKEN = props.getProperty("accessToken", ACCESS_TOKEN);

        // Check for comma-separated objectTypes first, fall back to single objectType
        String objectTypesStr = props.getProperty("objectTypes", null);
        boolean objectTypesExplicitlySet = false;
        if (objectTypesStr != null && !objectTypesStr.trim().isEmpty()) {
            OBJECT_TYPES = parseObjectTypes(objectTypesStr);
            objectTypesExplicitlySet = true;
        } else {
            OBJECT_TYPE = props.getProperty("objectType", OBJECT_TYPE);
        }

        try {
            BATCH_SIZE = Integer.parseInt(props.getProperty("batchSize", String.valueOf(BATCH_SIZE)));
            THREAD_COUNT = Integer.parseInt(props.getProperty("threadCount", String.valueOf(THREAD_COUNT)));
            SOQL_LIMIT = Integer.parseInt(props.getProperty("soqlLimit", String.valueOf(SOQL_LIMIT)));
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in properties: " + e.getMessage());
        }

        // Only load soql if objectTypes were not explicitly set
        if (!objectTypesExplicitlySet) {
            SOQL = props.getProperty("soql", SOQL);
        }
        CSV_FILE = props.getProperty("csvFile", CSV_FILE);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instanceUrl":
                    INSTANCE_URL = getArgValue(args, i + 1);
                    break;
                case "--accessToken":
                    ACCESS_TOKEN = getArgValue(args, i + 1);
                    break;
                case "--batchSize":
                    BATCH_SIZE = parseIntArg(args, i + 1, BATCH_SIZE);
                    break;
                case "--threadCount":
                    THREAD_COUNT = parseIntArg(args, i + 1, THREAD_COUNT);
                    break;
                case "--soqlLimit":
                    SOQL_LIMIT = parseIntArg(args, i + 1, SOQL_LIMIT);
                    break;
                case "--soql":
                    SOQL = getArgValue(args, i + 1);
                    break;
                case "--csvFile":
                    CSV_FILE = getArgValue(args, i + 1);
                    break;
                case "--objectType":
                    OBJECT_TYPE = getArgValue(args, i + 1);
                    break;
                case "--objectTypes":
                    OBJECT_TYPES = parseObjectTypes(getArgValue(args, i + 1));
                    objectTypesExplicitlySet = true;
                    break;
            }
        }

        // If objectTypes was not set, create a list with the single objectType
        if (OBJECT_TYPES == null) {
            OBJECT_TYPES = Arrays.asList(OBJECT_TYPE);
        }

        // If objectTypes were explicitly set, build SOQL from the first object type
        if (objectTypesExplicitlySet && (SOQL == null || SOQL.isEmpty())) {
            SOQL = "SELECT Id FROM " + OBJECT_TYPES.get(0) + " ORDER BY CreatedDate DESC";
        }
    }

    static List<String> parseObjectTypes(String objectTypesStr) {
        List<String> types = new ArrayList<>();
        String[] parts = objectTypesStr.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                types.add(trimmed);
            }
        }
        return types;
    }

    static String getArgValue(String[] args, int index) {
        return index < args.length ? args[index] : "";
    }

    static int parseIntArg(String[] args, int index, int defaultValue) {
        try {
            return index < args.length ? Integer.parseInt(args[index]) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static void processBatch(List<String> batch, int[] completedBatches, List<Long> batchTimes,
                            int totalBatches, int[] lastLoggedPercent, long[] lastLoggedTime, long startTime) {
        try {
            Thread.sleep(THREAD_TIMEOUT_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        long batchStart = System.currentTimeMillis();
        processBatchWork(batch);
        long batchEnd = System.currentTimeMillis();
        batchTimes.add(batchEnd - batchStart);

        synchronized (completedBatches) {
            completedBatches[0]++;
            int percent = (int) ((completedBatches[0] * 100.0) / totalBatches);
            if (percent / 5 > lastLoggedPercent[0] / 5) {
                long now = System.currentTimeMillis();
                long elapsed = now - lastLoggedTime[0];
                lastLoggedTime[0] = now;
                lastLoggedPercent[0] = percent;

                int recordsProcessed = completedBatches[0] * BATCH_SIZE;
                int incrementalRecords = (int) ((completedBatches[0] - lastBatchCount) * BATCH_SIZE);
                double incrementalThroughput = (incrementalRecords / (elapsed / 1000.0)) * 60.0;
                double avgThroughput = (recordsProcessed / ((now - startTime) / 1000.0)) * 60.0;
                lastBatchCount = completedBatches[0];

                System.out.printf(
                    "Progress: %d%% (%d/%d batches) - %s (%.2f sec) | Current: %.0f rec/min | Avg: %.0f rec/min\n",
                    percent, completedBatches[0], totalBatches, new java.util.Date(now), elapsed / 1000.0, incrementalThroughput, avgThroughput
                );
            }
        }
    }

    static void processBatchWork(List<String> batch) {
        try {
            String concatedIds = String.join(",", batch);
            String urlStr = INSTANCE_URL + "/services/apexrest/sobjectshares?objectApiName=" + OBJECT_TYPE + "&recordIds=" + concatedIds;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String resp = readAll(conn.getInputStream());
                List<String> recordIds = extractRecordsFromResponse(resp);
                if (!recordIds.isEmpty()) {
                    insertShareRecords(recordIds);
                }
            } else {
                String resp = readAll(conn.getErrorStream());
                System.err.println("Failed to get shares: " + resp);
            }
        } catch (Exception e) {
            System.err.println("Exception processing batch: " + e.getMessage());
        }
    }

    static List<String> extractRecordsFromResponse(String jsonResponse) {
        List<String> records = new ArrayList<>();
        try {
            int recordsIdx = jsonResponse.indexOf("\"records\"");
            if (recordsIdx == -1) return records;

            int arrayStart = jsonResponse.indexOf("[", recordsIdx);
            int arrayEnd = findMatchingBracket(jsonResponse, arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return records;

            String recordsArray = jsonResponse.substring(arrayStart + 1, arrayEnd);
            int objStart = 0;
            while ((objStart = recordsArray.indexOf("{", objStart)) != -1) {
                int objEnd = findMatchingBrace(recordsArray, objStart);
                if (objEnd == -1) break;

                String record = recordsArray.substring(objStart, objEnd + 1);
                records.add(record);
                objStart = objEnd + 1;
            }
        } catch (Exception e) {
            System.err.println("Error parsing records from response: " + e.getMessage());
        }
        return records;
    }

    static int findMatchingBrace(String str, int openPos) {
        int count = 1;
        for (int i = openPos + 1; i < str.length(); i++) {
            if (str.charAt(i) == '{') count++;
            else if (str.charAt(i) == '}') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    static int findMatchingBracket(String str, int openPos) {
        int count = 1;
        for (int i = openPos + 1; i < str.length(); i++) {
            if (str.charAt(i) == '[') count++;
            else if (str.charAt(i) == ']') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    static void insertShareRecords(List<String> records) throws Exception {
        if (records.isEmpty()) return;

        int batchSize = 200;
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            List<String> batch = records.subList(i, end);
            insertShareRecordsBatch(batch);
        }
    }

    static void insertShareRecordsBatch(List<String> records) throws Exception {
        try {
            StringBuilder body = new StringBuilder("{\"records\":[");
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) body.append(",");
                body.append(records.get(i));
            }
            body.append("]}");

            String urlStr = INSTANCE_URL + "/services/data/" + API_VERSION + "/composite/sobjects?allOrNone=false";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String resp = readAll(conn.getErrorStream());
                System.err.println("Failed to insert share records batch: " + resp);
            }
        } catch (Exception e) {
            System.err.println("Exception inserting share records batch: " + e.getMessage());
        }
    }

    static String submitBulkQuery(String soql) throws Exception {
        String urlStr = INSTANCE_URL + "/services/data/" + API_VERSION + "/jobs/query";
        String body = String.format("{\"operation\":\"query\",\"query\":\"%s\",\"contentType\":\"CSV\"}", soql.replace("\"", "\\\""));
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        String jobId = extractJsonValue(resp, "id");
        if (jobId == null) throw new RuntimeException("Could not parse jobId from response: " + resp);
        return jobId;
    }

    static void waitForJobCompletion(String jobId) throws Exception {
        String urlStr = INSTANCE_URL + "/services/data/" + API_VERSION + "/jobs/query/" + jobId;
        while (true) {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(120000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            String state = extractJsonValue(resp, "state");
            if ("JobComplete".equals(state)) break;
            if ("Failed".equals(state) || "Aborted".equals(state)) throw new RuntimeException("Job failed: " + state);
            Thread.sleep(2000);
        }
    }

    static long streamBulkQueryResultsToFile(String jobId, File tempFile) throws Exception {
        long count = 0;
        String firstId = null;
        String lastId = null;
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String urlStr = INSTANCE_URL + "/services/data/" + API_VERSION + "/jobs/query/" + jobId + "/results";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(120000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            conn.setRequestProperty("Accept", "text/csv");

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header == null) {
                    System.err.println("No CSV header returned by Bulk API.");
                    return 0;
                }

                int idIdx = -1;
                String[] headers = header.split(",");
                for (int i = 0; i < headers.length; i++) {
                    String col = headers[i].replaceAll("\"", "").trim();
                    if (col.equalsIgnoreCase("Id")) {
                        idIdx = i;
                        break;
                    }
                }
                if (idIdx == -1) {
                    throw new RuntimeException("No Id column in CSV header: " + header);
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] cols = line.split(",");
                    if (cols.length > idIdx) {
                        String id = cols[idIdx].replaceAll("\"", "").trim();
                        if (!id.isEmpty()) {
                            writer.println(id);
                            if (firstId == null) {
                                firstId = id;
                            }
                            lastId = id;
                            count++;
                        }
                    }
                }
            }
            conn.disconnect();
        }
        if (firstId != null) {
            System.out.println("Bulk API Job " + jobId + " - First ID: " + firstId + " | Last ID: " + lastId);
        }
        return count;
    }

    static Iterator<String> loadIdsFromCsvStream(String filePattern) throws Exception {
        return new CsvIdIterator(filePattern);
    }

    static long streamIdsToFile(Iterator<String> idIterator, File tempFile) throws IOException {
        long count = 0;
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            while (idIterator.hasNext()) {
                String id = idIterator.next();
                writer.println(id);
                count++;
            }
        }
        return count;
    }

    static class CsvIdIterator implements Iterator<String> {
        private BufferedReader reader;
        private String nextId;
        private boolean isWildcard;
        private Iterator<Path> fileIterator;
        private int idIdx;

        CsvIdIterator(String filePattern) throws Exception {
            this.isWildcard = filePattern.contains("*") || filePattern.contains("?");
            if (isWildcard) {
                initWildcardIterator(filePattern);
            } else {
                initSingleFileReader(filePattern);
            }
            advance();
        }

        private void initSingleFileReader(String filePath) throws Exception {
            reader = new BufferedReader(new FileReader(filePath));
            String header = reader.readLine();
            if (header == null) {
                throw new RuntimeException("CSV file is empty.");
            }
            findIdColumn(header);
        }

        private void initWildcardIterator(String filePattern) throws Exception {
            int lastSeparator = Math.max(filePattern.lastIndexOf('/'), filePattern.lastIndexOf('\\'));
            String basePath = lastSeparator > 0 ? filePattern.substring(0, lastSeparator) : ".";
            String pattern = lastSeparator > 0 ? filePattern.substring(lastSeparator + 1) : filePattern;

            Path baseDir = Paths.get(basePath);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            List<Path> matchingFiles = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(baseDir, 1)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .sorted()
                    .forEach(matchingFiles::add);
            }
            this.fileIterator = matchingFiles.iterator();
            if (fileIterator.hasNext()) {
                initSingleFileReader(fileIterator.next().toString());
            }
        }

        private void findIdColumn(String header) {
            idIdx = -1;
            String[] headers = header.split(",");
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].replaceAll("\"", "").trim().equalsIgnoreCase("Id")) {
                    idIdx = i;
                    break;
                }
            }
        }

        private void advance() throws Exception {
            nextId = null;
            while (nextId == null) {
                if (reader != null) {
                    String line = reader.readLine();
                    if (line != null) {
                        String[] cols = line.split(",");
                        if (cols.length > idIdx) {
                            nextId = cols[idIdx].replaceAll("\"", "").trim();
                            if (nextId.isEmpty()) {
                                nextId = null;
                            }
                        }
                    } else {
                        reader.close();
                        reader = null;
                        if (isWildcard && fileIterator.hasNext()) {
                            initSingleFileReader(fileIterator.next().toString());
                        } else {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextId != null;
        }

        @Override
        public String next() {
            if (nextId == null) {
                throw new NoSuchElementException();
            }
            String result = nextId;
            try {
                advance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return result;
        }
    }

    static String extractJsonValue(String json, String key) {
        java.util.regex.Pattern JSON_EXTRACTOR = java.util.regex.Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        java.util.regex.Matcher matcher = JSON_EXTRACTOR.matcher(json);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
}
 