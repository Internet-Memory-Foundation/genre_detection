package net.internetmemory.genreclassification;

import com.martinkl.warc.WARCWritable;
import com.martinkl.warc.mapreduce.WARCInputFormat;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jie on 11/07/17.
 */
public class ClassificationWarc {
    private static SparkConf sparkConf = new SparkConf()
            .setAppName("WebsiteCategorizor")
            .setMaster("local[*]");
    private static JavaSparkContext sc = new JavaSparkContext(sparkConf);
    private static Pattern pld = Pattern.compile("^([^/]+//[^/]+)($|/)");
    private static Pattern head = Pattern.compile("<html>|<!doctype", Pattern.CASE_INSENSITIVE);
    private static Pattern statusCode = Pattern.compile("HTTP/1\\.\\d (\\d{3})");
    private static Configuration conf = new Configuration();
    private static List<String> order = Arrays.asList("Forum", "News", "Blogs","Marketplace", "Spam", "Porn");
    //private static List<String> order = Arrays.asList("Spam", "Non-Spam");
    private static List<String> suffixes = Arrays.asList(".jpg", ".txt", ".ico", ".js", ".css", ".gif");

    public static class Webpage implements Serializable {
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

    static Map<String, List<Double>> classify(String warcDir, String modelPath) throws XGBoostError, IOException, ClassNotFoundException {
        Job job = Job.getInstance(conf);
        List<Map<String, List<Double>>> resultMapList = new ArrayList<>();
        File[] warcFiles = new File(warcDir).listFiles();
        ClassificationWekaModel model = new ClassificationWekaModel();
        model.loadModel(modelPath);
        int passed = 0;
        for(File file: warcFiles){
            JavaPairRDD<LongWritable, WARCWritable> records = sc.newAPIHadoopFile(file.getPath(),
                    WARCInputFormat.class, LongWritable.class, WARCWritable.class, job.getConfiguration());
            JavaRDD<Webpage> webpagesRDD = records
                    .filter(record -> record._2().getRecord().getHeader().getRecordType().equals("response") &&
                            new String(record._2().getRecord().getContent()).contains("Content-Type: text/html"))
                    .filter(record -> {
                        String url = record._2().getRecord().getHeader().getTargetURI();
                        boolean isHtml = true;
                        for(String suffix: suffixes){
                            if(url.endsWith(suffix)){
                                isHtml = false;
                                break;
                            }
                        }
                        String content = new String(record._2().getRecord().getContent());

                        return isHtml && head.matcher(content).find();
                    })
                    .map(record -> {
                        String url = record._2().getRecord().getHeader().getTargetURI();
                        String content = new String(record._2().getRecord().getContent());

                        String part2 = content.split("<", 2)[1];
                        String html = "<" + part2;

                        String status = "";
                        Matcher mstatus = statusCode.matcher(content);
                        if (mstatus.find()) {
                            status = mstatus.group(1);
                        }
                        return new Webpage(url, html, status);
                    }).filter(page -> page.getStatus().equals("200"));
            Map<String, List<Double>> resultMap = webpagesRDD.mapToPair(webpage -> {
                String p = getPLD(webpage.getUrl());
                double[] result;
                try {
                    result = model.predict(webpage.getContent(), webpage.getUrl());
                } catch (Exception e) {
                    return new Tuple2<>(p, Arrays.asList(0., 0., 0., 0., 0., 0., 0.));
                }
                //int resultIdx = order.indexOf(result);
                List<Double> temp = new ArrayList<>();
                for(double proba: result){
                    temp.add(proba);
                }
                //temp.set(resultIdx, 1);
                return new Tuple2<>(p, temp);
            }).reduceByKey((list1, list2) -> {
                List<Double> list = new ArrayList<>();
                for (int i = 0; i < list1.size(); i++) {
                    list.add(list1.get(i) + list2.get(i));
                }
                return list;
            }).collectAsMap();
            resultMapList.add(resultMap);
        }
        System.out.println(passed + " warcs passed.");
        return normalize(mergeMap(resultMapList));
    }

    static String getPLD(String url) throws MalformedURLException {
        Matcher m = pld.matcher(url);
        String p = null;
        if(m.find()){
            p = m.group(1);
        }
        return new URL(p).getHost();
    }

    static Map<String, List<Double>> mergeMap(List<Map<String, List<Double>>> mapList){
        Map<String, List<Double>> merged = new Hashtable<>();
        for(Map<String, List<Double>> map: mapList){
            for(Map.Entry<String, List<Double>> entry: map.entrySet()){
                if(merged.containsKey(entry.getKey())){
                    List<Double> temp = new ArrayList<>();
                    for(int i=0; i<entry.getValue().size(); i++){
                        temp.add(entry.getValue().get(i) + merged.get(entry.getKey()).get(i));
                    }
                    merged.put(entry.getKey(), temp);
                }else{
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return merged;
    }

    static Map<String, List<Double>> normalize(Map<String, List<Double>> mergedMap){
        for(Map.Entry<String, List<Double>> entry: mergedMap.entrySet()){
            double sum = 0;
            for(double proba: entry.getValue().subList(0,5)){
                sum += proba;
            }
            List<Double> normalizedProba = new ArrayList<>();
            for(int i=0; i<entry.getValue().size(); i++){
                normalizedProba.add(entry.getValue().get(i)/sum);
            }
            normalizedProba.add(sum);
            mergedMap.put(entry.getKey(), normalizedProba);
        }
        return mergedMap;
    }

    public static void main(String[] args) throws XGBoostError, IOException, ClassNotFoundException {
        Map<String, List<Double>> result =
                classify("/home/jie/warcs", "/home/jie/projects/GenreClassification/model/model.bin");
        List<Triple<String, List<Double>, String>> toDump = new ArrayList<>();
        result.forEach((k, v) -> {
            String cls = "";
            if(v.get(5) > 0.5){
                cls = "Porn";
            }else {
                double max = Collections.max(v.subList(0, 5));
                int pos = v.indexOf(max);
                cls = order.get(pos);
            }
            toDump.add(Triple.of(k, v, cls));
        });
        toDump.sort(Comparator.comparingDouble(t -> -Collections.max(t.getMiddle())));
        FileUtils.writeLines(new File("/home/jie/Desktop/ClassificationNew.txt"), toDump);
    }
}
