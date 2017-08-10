package net.internetmemory.genreclassification;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FilterJson {
    public static void main(String[] args) throws IOException {
        File annotation = new File("/home/jie/projects/GenreClassification/porn3/porn.json");
        String content = FileUtils.readFileToString(annotation);
        JSONArray jsonArray = new JSONArray(content);
        /*
        JSONArray filteredArray = new JSONArray();
        for(Object record: jsonArray){
            if(((JSONObject) record).getString("html").length() > 3000){
                filteredArray.put(record);
            }
        }
        FileUtils.writeStringToFile(new File("/home/jie/projects/GenreClassification/porn3/porn_new.json"),
                filteredArray.toString(4));
                */
        List<String> urls = new ArrayList<>();
        for(Object record: jsonArray){
            urls.add(((JSONObject) record).getString("url"));
        }
        FileUtils.writeLines(new File("/home/jie/projects/GenreClassification/porn3/url_list.txt"), urls);
    }
}
