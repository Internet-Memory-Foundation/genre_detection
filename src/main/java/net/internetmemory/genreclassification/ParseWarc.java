package net.internetmemory.genreclassification;

import SparkWARC.WARC;
import org.apache.commons.io.IOUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jie on 10/07/17.
 */
public class ParseWarc {
    private static Pattern url = Pattern.compile("WARC-Target-URI: (.+)");
    private static Pattern status = Pattern.compile("HTTP/1\\.\\d (\\d{3})");

    public ParseWarc(){

    }

    public static class Webpage implements Serializable{
        private String url;
        private String content;
        private String status;

        public Webpage(String url, String content, String status){
            this.url = url;
            this.content = content;
            this.status = status;
        }

        public String getContent() {
            return content;
        }

        public String getStatus() {
            return status;
        }

        public String getUrl() {
            return url;
        }
    }
    private static <T> Iterable<T> iterable(final Iterator<T> it){
        return () -> it;
    }

    public static List<Webpage> parseWarc(Iterator<Row> raw){
        List<Webpage> webpages = new ArrayList<>();
        boolean start = false;
        String u = null;
        String s = null;
        boolean docStart = false;
        StringBuilder sb = new StringBuilder();
        for(Row row: iterable(raw)){
            String line = row.getString(0);
            if(line.equals("WARC-Type: response")){
                start = true;
            }
            if(line.equals("WARC/1.0")){
                start = false;
                if(docStart){
                    webpages.add(new Webpage(u, sb.toString(), s));
                    docStart = false;
                }
                u = null;
                s = null;
                sb = new StringBuilder();
            }
            else if(start){
                Matcher murl = url.matcher(line);
                Matcher mstatus = status.matcher(line);
                if(murl.find()){
                    u = murl.group(1);
                }
                if(mstatus.find()){
                    s = mstatus.group(1);
                }
                if(line.contains("<!DOCTYPE")){
                    docStart = true;
                }
                if(docStart){
                    sb.append(line).append("\n");
                }
            }
        }
        return webpages;
    }

}
