package net.internetmemory.genreclassification;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by jie on 16/06/17.
 */
public class MakeAnnotation {
    private static final Pattern urlPattern = Pattern.compile("<!-- URL: (.+) -->");
    private static final Pattern pldPattern = Pattern.compile("//([^/]+)/");
    /*
    private static JSONObject cleanSpamHtml(List<String> lines){
        List<String> cleanedHtml = new ArrayList<>();
        Matcher m = urlPattern.matcher(lines.get(0));
        String url = "";
        if(m.find()){
            url = m.group(1);
        }
        Matcher m2 = pldPattern.matcher(url);
        String pld = "";
        if(m2.find()){
            pld = m2.group(1);
        }
        boolean start = true;
        for(String line: lines){
            if(!line.startsWith("<!-- ")){
                start = false;
            }
            if(!start){
                cleanedHtml.add(line);
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", url);
        jsonObject.put("pld", pld);
        jsonObject.put("html", StringUtils.join(cleanedHtml, "\n"));
        jsonObject.put("label", "Spam");
        return jsonObject;
    }*/

    private static JSONObject cleanSpamHtml(List<String> lines, String url){
        url = url.replaceAll(".html", "").replaceAll(".htm", "");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", "http://"+url);
        jsonObject.put("pld", "");
        jsonObject.put("html", StringUtils.join(lines, "\n"));
        jsonObject.put("label", "Spam");
        return jsonObject;
    }

    public static void main(String[] args) throws IOException {
        File annotation = new File("/home/jie/projects/GenreClassification/annotation.json");
        String content = FileUtils.readFileToString(annotation);
        JSONArray jsonArray = new JSONArray(content);
        File annotation2 = new File("/home/jie/projects/GenreClassification/annotation_2.json");
        String content2 = FileUtils.readFileToString(annotation2);
        JSONArray jsonArray2 = new JSONArray(content2);
        // to modify dataset
        File annotationPorn = new File("/home/jie/projects/GenreClassification/porn/porn_new.json");
        String contentPorn = FileUtils.readFileToString(annotationPorn);
        JSONArray jsonArrayPorn = new JSONArray(contentPorn);
        File[] spamFiles = new File("/home/jie/projects/GenreClassification/spam_all").listFiles();
        int sizeBefore = jsonArray.length();
        Arrays.stream(spamFiles).forEach(f -> {
            try {
                jsonArray.put(cleanSpamHtml(FileUtils.readLines(f), f.getName().toLowerCase()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        //assert sizeBefore + spamFiles.length == jsonArray.length();
        //sizeBefore = jsonArray.length();
        int numMarketplace = 0;
        for(Object obj: jsonArray){
            if(((JSONObject)obj).getString("label").equals("Marketplace")){
                    numMarketplace++;
            }
        }
        for(Object obj: jsonArray2){
            if(((JSONObject)obj).getString("label").equals("Marketplace")){
                if(numMarketplace < 500){
                    jsonArray.put(obj);
                    numMarketplace++;
                }
            }else{
                jsonArray.put(obj);
            }
        }
        //assert sizeBefore + jsonArray2.length() == jsonArray.length();
        //sizeBefore = jsonArray.length();
        for(Object obj: jsonArrayPorn){
            jsonArray.put(obj);
        }
        //assert sizeBefore + jsonArrayPorn.length() == jsonArray.length();
        JSONArray filteredArray = new JSONArray();
        int filtered = 0;
        for(Object obj: jsonArray){
            try{
                Jsoup.parse(((JSONObject) obj).getString("html"));
                if(!((JSONObject) obj).get("label").equals("Other")) {
                    filteredArray.put(obj);
                }
            }catch (Exception e){
                filtered++;
            }
        }
        System.out.println("Filtered: "+filtered);
        Map<String, Long> labels = filteredArray.toList().stream().map(obj -> {
            HashMap object = (HashMap) obj;
            return object.get("label").toString();
        }).collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        labels.forEach((k, v) -> System.out.printf("%s: %d\n", k, v));
        FileUtils.writeStringToFile(new File("/home/jie/projects/GenreClassification/annotation_new.json"),
                filteredArray.toString(4));
    }
}
