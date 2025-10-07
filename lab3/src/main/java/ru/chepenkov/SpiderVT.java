package ru.chepenkov;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

public class SpiderVT {
    private final String baseUrl;
    private final HttpClient http;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger active = new AtomicInteger(0);

    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    public SpiderVT(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void start() throws InterruptedException {
        queue.add("/");

        while (true) {
            String path = queue.poll(2, TimeUnit.SECONDS);
            if (path == null) {
                if (active.get() == 0) break;
                else continue;
            }
            if (!visited.add(path)) {
                continue;
            }

            active.incrementAndGet();
            exec.submit(() -> {
                try {
                    fetch(path);
                } finally {
                    active.decrementAndGet();
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.MINUTES);
    }

    private void fetch(String path) {
        if (!path.startsWith("/")) path = "/" + path;
        URI uri = URI.create(baseUrl + path);


        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                ParsedResponse pr = parse(resp.body());
                if (pr.message != null) messages.add(pr.message);
                if (pr.successors != null) {
                    for (String succ : pr.successors) {
                        if (!visited.contains(succ)) {
                            queue.add(succ);
                        }
                    }
                }
            } else {
                System.err.println("Bad response for " + path + " : " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Request failed for " + path + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();

            System.out.println("Fetching " + uri);
        }
    }

    private static final Pattern MESSAGE_RE = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
    private static final Pattern SUCCESSORS_RE = Pattern.compile("\"successors\"\\s*:\\s*\\[(.*?)\\]");
    private static final Pattern QUOTED_ITEM = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"");

    private ParsedResponse parse(String json) {
        String msg = null;
        Matcher m = MESSAGE_RE.matcher(json);
        if (m.find()) msg = unescape(m.group(1));

        List<String> succ = new ArrayList<>();
        Matcher ms = SUCCESSORS_RE.matcher(json);
        if (ms.find()) {
            Matcher it = QUOTED_ITEM.matcher(ms.group(1));
            while (it.find()) succ.add(unescape(it.group(1)));
        }
        return new ParsedResponse(msg, succ);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private record ParsedResponse(String message, List<String> successors) {}

    public List<String> getSortedMessages() {
        List<String> copy;
        synchronized (messages) {
            copy = new ArrayList<>(messages);
        }
        Collections.sort(copy);
        return copy;
    }

    public static void main(String[] args) throws Exception {
        String studentId = args.length > 0 ? args[0] : "IvanIvanov";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        SpiderVT spider = new SpiderVT("0.0.0.0", port);

        long start = System.currentTimeMillis();
        spider.start();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Collected " + spider.messages.size() + " messages in " + elapsed + " ms");
        System.out.println("----- Sorted messages -----");
        for (String msg : spider.getSortedMessages()) {
            System.out.println(msg);
        }
    }
}

