package ru.chepenkov;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.*;

public class KeyGenServer {

    private final ConcurrentHashMap<String, CompletableFuture<KeyEntry>> store = new ConcurrentHashMap<>();
    private final ExecutorService genPool;
    private final PrivateKey issuerKey;
    private final X500Name issuerName;
    private final int rsaBits = 8192;
    private final long certValidityDays = 3650;

    record KeyEntry(PrivateKey priv, PublicKey pub, X509Certificate cert) {}

    public KeyGenServer(PrivateKey issuerKey, X500Name issuerName, int genThreads) {
        this.issuerKey = issuerKey;
        this.issuerName = issuerName;
        this.genPool = Executors.newFixedThreadPool(genThreads);
        Security.addProvider(new BouncyCastleProvider());
    }

    public void run(int port) throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket client = server.accept();
                Thread.ofVirtual().start(() -> handleClient(client));
            }
        }
    }

    private void handleClient(Socket sock) {
        try (sock;
             InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            String name = readName(in);
            System.out.println("Request for: " + name + " from " + sock.getRemoteSocketAddress());

            CompletableFuture<KeyEntry> cf = store.computeIfAbsent(name, n -> {
                CompletableFuture<KeyEntry> f = new CompletableFuture<>();
                genPool.submit(() -> {
                    try {
                        KeyEntry ke = generateAndSign(n);
                        f.complete(ke);
                        System.out.println("Generated for " + n);
                    } catch (Throwable t) {
                        f.completeExceptionally(t);
                        t.printStackTrace();
                    }
                });
                return f;
            });

            KeyEntry ke = cf.get();
            byte[] privPem = toPem(ke.priv()).getBytes(StandardCharsets.US_ASCII);
            byte[] certPem = toPem(ke.cert()).getBytes(StandardCharsets.US_ASCII);

            out.write(intToBytes(privPem.length));
            out.write(privPem);
            out.write(intToBytes(certPem.length));
            out.write(certPem);
            out.flush();

        } catch (Exception e) {
            System.err.println("Client error: " + e);
        }
    }

    private String readName(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) buf.write(b);
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private KeyEntry generateAndSign(String subjectName) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(rsaBits);
        KeyPair kp = kpg.generateKeyPair();

        X500Name subj = new X500Name("CN=" + subjectName);
        BigInteger serial = new BigInteger(160, new SecureRandom());
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(Duration.ofDays(1)));
        Date notAfter = Date.from(now.plus(Duration.ofDays(certValidityDays)));

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, notBefore, notAfter, subj, kp.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA512withRSA")
                .setProvider("BC").build(issuerKey);

        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(holder);

        return new KeyEntry(kp.getPrivate(), kp.getPublic(), cert);
    }

    private static String toPem(Object obj) throws IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(obj);
        }
        return sw.toString();
    }

    private static byte[] intToBytes(int x) {
        return new byte[] {
                (byte)(x >>> 24),
                (byte)(x >>> 16),
                (byte)(x >>> 8),
                (byte)x
        };
    }

    public static PrivateKey loadPrivateKeyFromPem(File pemFile) throws Exception {
        try (Reader r = new FileReader(pemFile);
             PEMParser pp = new PEMParser(r)) {
            Object obj = pp.readObject();
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
            if (obj == null) throw new IllegalArgumentException("No object found in PEM");
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair) {
                KeyPair kp = conv.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) obj);
                return kp.getPrivate();
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                return conv.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) obj);
            } else {
                throw new IllegalArgumentException("Unsupported PEM object: " + obj.getClass());
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java KeyGenServerVT <port> <issuerKeyPemFile> <issuerDN> [genThreads]");
            return;
        }
        int port = Integer.parseInt(args[0]);
        File issuerPem = new File(args[1]);
        String issuerDN = args[2];
        int genThreads = args.length >= 4 ? Integer.parseInt(args[3]) :
                Runtime.getRuntime().availableProcessors();

        PrivateKey issuerKey = loadPrivateKeyFromPem(issuerPem);
        X500Name issuerName = new X500Name(issuerDN);

        new KeyGenServer(issuerKey, issuerName, genThreads).run(port);
    }
}
