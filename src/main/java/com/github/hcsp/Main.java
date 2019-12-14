package com.github.hcsp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws IOException, SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:/D:/crawler/news-crawer/news", USER_NAME, PASSWORD);
        try (PreparedStatement statement = connection.prepareStatement("insert into links_to_be_processed values('https://sina.cn')")) {
            statement.execute();
        }
        try {
            while (true) {
                //待处理的链接池
                //从数据库加载即将处理的链接的代码
                List<String> linkPool = loadUrlsFromDatabase(connection, "select link from links_to_be_processed");
                //已经处理的链接池
                //从数据库加载已经处理的链接的代码

                if (linkPool.isEmpty()) {
                    break;
                }
                //ArrayList从尾部删除更有效率
                //每次处理完后更新数据库
                //处理完后从池子(包括数据库)中删除
                String link = linkPool.remove(linkPool.size() - 1);
                insertLinkIntoDatabase(connection, link, "delete from links_to_be_processed where link = ?");

                if (isLinkProcessed(connection, link)) {
                    continue;
                }

                if (isInterestedLink(link)) {
                    if (link.charAt(6) == '\\') {
                        continue;
                    }
                    Document doc = httpGetAndParseHtml(link);

                    parseUrlsFromPageAndStoreIntoDatabase(connection, doc);

                    storeIntoDatabaseIfItIsNewsPage(doc);

                    insertLinkIntoDatabase(connection, link, "insert into links_already_processed (link) values(?)");
                }
            }
        } finally {
            System.out.println("Exit");
        }
    }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Connection connection, Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            insertLinkIntoDatabase(connection, href, "insert into links_to_be_processed (link) values(?)");
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement("select link from links_already_processed where link = ?")) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return false;
    }

    private static void insertLinkIntoDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    private static List<String> loadUrlsFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
    }

    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    private static Document httpGetAndParseHtml(String link) throws IOException {
        //这是我们感兴趣的，我们只处理新浪站内的链接
        CloseableHttpClient httpclient = HttpClients.createDefault();
        if (link.startsWith("//")) {
            link = "https:" + link;
        }

        System.out.println(link);
        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");
        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            String html = EntityUtils.toString(entity1);
            return Jsoup.parse(html);
        }
    }

    private static boolean isInterestedLink(String link) {
        return (isIndexPage(link) || isNewsLink(link) && isNotLoginPage(link));
    }

    private static boolean isIndexPage(String link) {
        return link.equals("https://sina.cn");
    }

    private static boolean isNewsLink(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport");
    }
}
