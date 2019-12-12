package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        //待处理的链接池
        List<String> linkpool = new ArrayList<>();
        //已经处理的链接池
        Set<String> processedLinks = new HashSet<>();
        linkpool.add("https://sina.cn");
        while (true) {
            if (linkpool.isEmpty()) {
                break;
            }
            //ArrayList从尾部删除更有效率
            String link = linkpool.remove(linkpool.size() - 1);
            if (processedLinks.contains(link)) {
                continue;
            }
            if (isInterestedLink(link)) {
                //这是我们感兴趣的，我们只处理新浪站内的链接
                if (link.charAt(6) == '\\') {
                    continue;
                }
               Document doc =  httpGetAndParseHtml(link);
               doc.select("a").stream().map(aTag -> aTag.attr("href")).forEach(linkpool::add);
                //假如这是一个新闻页面，就存入数据库，否则就什么都不做
                storeIntoDatabaseIfItIsNewsPage(doc);
                processedLinks.add(link);
            }

        }
    }




    public static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    private static Document httpGetAndParseHtml(String link) throws IOException {
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
