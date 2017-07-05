package com.alekseysamoylov.cuncurrent.filter;



import com.alekseysamoylov.cuncurrent.filter.exception.BlackListCriticalException;
import com.alekseysamoylov.cuncurrent.filter.exception.BlackListException;
import com.alekseysamoylov.cuncurrent.utils.MessageSourceHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BlackListParallelFilter {

    private static final List<String> BLACK_LIST_IP = new CopyOnWriteArrayList<>();

    private static final String BLACK_LIST_FILE_DIRECTORY = "settings";
    private static final String BLACK_LIST_FILE = "black-list.xml";
    private static final String IP_ELEMENT_EXPRESSION = "/black-list/ip";
    private volatile static boolean applicationIsRunning = false;


    public static boolean shouldBlock(Request request) {
        return getList().contains(request.getIpAddress());
    }

    private static List<String> getList() {
        Path blackListPath = Paths.get(BLACK_LIST_FILE_DIRECTORY, BLACK_LIST_FILE);
        if (!applicationIsRunning) {
            try {
                if (Files.exists(blackListPath)) {
                    fillBlackList(BLACK_LIST_IP, blackListPath);
                } else {
                    createNewFile(blackListPath);
                }
            } catch (ParserConfigurationException | BlackListException e) {
                System.err.println("Something was wrong with first reading black-list.xml " + e.getMessage());
            }
            synchronized (BlackListParallelFilter.class) {
                if (!applicationIsRunning) {
                    applicationIsRunning = true;

                    Executor executor = Executors.newSingleThreadExecutor();

                    executor.execute(() -> {
                        try {
                            getFileToSearchChangesParseAndFillIpList(blackListPath);
                        } catch (IOException | InterruptedException | BlackListException | ParserConfigurationException e) {
                            applicationIsRunning = false;
                            BLACK_LIST_IP.clear();
                            System.err.println("Something was wrong in working with black ip list " + e.getMessage());
                            throw new BlackListCriticalException("Something was wrong in working with black ip list ", e);
                        }
                    });
                }
            }
        }

        if (!Files.exists(blackListPath)) {
            applicationIsRunning = false;
        }

        return BLACK_LIST_IP;

    }

    public static void shutDown() {
        applicationIsRunning = false;
    }


    private static Set<String> getIpSetFromDocument(Document document) {
        Set<String> ipSet = new HashSet<>();

        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            NodeList nodeList = (NodeList) xPath.compile(IP_ELEMENT_EXPRESSION)
                    .evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node nNode = nodeList.item(i);

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String ip = eElement
                            .getTextContent();
                    ipSet.add(ip);
                }
            }

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return ipSet;
    }

    private static void getFileToSearchChangesParseAndFillIpList(Path blackListPath) throws IOException, InterruptedException, BlackListException, ParserConfigurationException {

        try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
            blackListPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            while (applicationIsRunning) {
                final WatchKey innerWatchKey = watchService.take();
                for (WatchEvent<?> event : innerWatchKey.pollEvents()) {
                    //we only register "ENTRY_MODIFY" so the context is always a Path.
                    final Path changed = (Path) event.context();
                    if (changed.endsWith(BLACK_LIST_FILE)) {
                        System.out.println("Logger.info: black-list.xml was changed. " +
                                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                        fillBlackList(BLACK_LIST_IP, blackListPath);
                    }
                }
                // reset the key
                boolean valid = innerWatchKey.reset();
                if (!valid) {
                    System.out.println("Key has been unregisterede");
                }
            }
        }
    }

    private static void fillBlackList(List<String> blackListIp, Path blackListPath) throws ParserConfigurationException, BlackListException {
        DocumentBuilderFactory documentBuilderFactory
                = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder;

        documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Optional<Document> documentOptional = Optional.empty();

        try (InputStream inputStream = Files.newInputStream(blackListPath)) {
            if (documentBuilder != null) {
                Document doc = documentBuilder.parse(inputStream);
                documentOptional = Optional.ofNullable(doc);
            } else {
                System.err.println("Logger.error: Cannot parse black-list.xml file.");
            }
        } catch (IOException | SAXException e) {
            System.err.println("Logger.error: Cannot parse black-list.xml file." + e.getMessage());
            throw new BlackListException("Logger.error: Cannot parse black-list.xml file.", e);
        }
        if (documentOptional.isPresent()) {
            Document document = documentOptional.get();
            document.getDocumentElement().normalize();

            Set<String> ipSet = getIpSetFromDocument(document);
            System.out.println("Logger.debug: black ip list readed from file: " + ipSet);
            blackListIp.clear();
            blackListIp.addAll(ipSet);

        }
    }

    private static void createNewFile(Path blackListPath) throws BlackListException {
        System.out.print("Logger.debug: Try to create new black-list.xml file");
        try {
            Files.createDirectories(blackListPath.getParent());
            Files.createFile(blackListPath);
        } catch (IOException e) {
            System.err.println(MessageSourceHelper.getMessage("exception.black.list.file.not.created"));
            throw new BlackListException(MessageSourceHelper.getMessage("exception.black.list.file.not.created"), e);
        }
    }
}
