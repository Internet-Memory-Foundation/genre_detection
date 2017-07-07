package net.internetmemory.genreclassification;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


class ClassificationBuildData {
	
	private List<String> nameStatisticsFeatures;	
	private Instances statisticsData;
	private Instances urlData;
	private List<String> listLabel;
	
	private static List<String> classes;
	
	ClassificationBuildData(List<String> _classes, List<String> nameStatisticsFeatures){
		classes = _classes;
		this.nameStatisticsFeatures = nameStatisticsFeatures == null ? new ArrayList<>() : nameStatisticsFeatures;
	}	

	List<String> getNameStatisticsFeatures(){
		return nameStatisticsFeatures;
	}

	Instances getStatisticsData(){
		return statisticsData;
	}

	Instances getUrlData(){
		return urlData;
	}

	List<String> getListLabel() {
		return listLabel;
	}

	DMatrix getMetaData(float[][] resultXGB, List<double[]> resultLR, List<String> listLabels) throws IOException, XGBoostError {
		return buildMetaData(resultXGB, resultLR, listLabels);
	}
	
	private void findNameStatisticsFeatures(List<ClassificationHTMLFeatures> li){
		for(ClassificationHTMLFeatures ele : li){
			for(String name : ele.getHtmlStatistics().keySet()){
				if(!nameStatisticsFeatures.contains(name))
					nameStatisticsFeatures.add(name);
			}
		}
		Collections.sort(nameStatisticsFeatures);
		nameStatisticsFeatures.add("@@class@@");  // class attribute is always the last attribute
	}
	
	private void makeStatisticsInstances(String relationName, int capacity){
		ArrayList<Attribute> listAttr = new ArrayList<>();
		String name = "";
		for(int i = 0; i < nameStatisticsFeatures.size() - 1; i++){
			name = nameStatisticsFeatures.get(i);
			listAttr.add(new Attribute(name));
		}	
		name = nameStatisticsFeatures.get(nameStatisticsFeatures.size() - 1);
		Attribute attributeClass = new Attribute(name, classes);
		listAttr.add(attributeClass);
		statisticsData = new Instances(relationName, listAttr, capacity);	
		statisticsData.setClassIndex(statisticsData.numAttributes()-1);
	}

	void buildTrainData(List<Triple<String, String, String>> triples) throws Exception {
	    buildData(triples, "train");
    }

    void buildEvalData(List<Triple<String, String, String>> triples) throws Exception {
	    buildData(triples, "eval");
    }

	void buildTestData(List<Triple<String, String, String>> triples) throws Exception {
		buildData(triples, "test");
	}

	static DMatrix buildMetaData(float[][] resultXGB, List<double[]> resultLR, List<String> listLabel) throws IOException, XGBoostError {
		List<String> lines = new ArrayList<>();
		for(int i=0; i<resultLR.size(); i++){
			StringBuilder sb = new StringBuilder();
			sb.append((float) classes.indexOf(listLabel.get(i)));
			for(int j=0; j<resultXGB[0].length; j++){
				sb.append(" ").append(j+1).append(":").append(resultXGB[i][j]);
			}
			for(int j=0; j<resultLR.get(0).length; j++){
				sb.append(" ").append(j+resultLR.get(0).length+1).append(":")
						.append((float) resultLR.get(i)[j]);
			}
			lines.add(sb.toString());
		}
		FileUtils.writeLines(new File(ClassificationWekaModel.modelFolder+"metaTraining.libsvm"), lines);
		return new DMatrix(ClassificationWekaModel.modelFolder+"metaTraining.libsvm");
	}

	private void makeInstances(List<String> listHtml, List<String> listLabel, List<ClassificationHTMLFeatures> listPage){
		// html statistics features
		makeStatisticsInstances("statistics_data", listHtml.size());
		for(int i = 0; i < listHtml.size(); i++){
			statisticsData.add(buildStatInstance(listPage.get(i), statisticsData, listLabel.get(i)));
		}

		//url features
		Map<String, Class> schemaURL = new Hashtable<String, Class>(){{put("text", String.class);}};
		urlData = makeStructuralInstance("url_data", schemaURL, classes, listHtml.size());
		for(int i = 0; i < listHtml.size(); i++){
			urlData.add(makeInstance(urlData, listPage.get(i).getTokenizedURL(), listLabel.get(i)));
		}
	}
	
	private void buildData(List<Triple<String, String, String>> triples, String phase) throws Exception {
		List<String> listHtml = triples.stream().map(Triple::getLeft).collect(Collectors.toList());
		List<String> listUrl = triples.stream().map(Triple::getMiddle).collect(Collectors.toList());
		listLabel = triples.stream().map(Triple::getRight).collect(Collectors.toList());
		List<ClassificationHTMLFeatures> listPage = new ArrayList<>();
		for(int i = 0; i < listHtml.size(); i++){
			Document htmlParsed = Jsoup.parse(listHtml.get(i));
			ClassificationHTMLFeatures page = new ClassificationHTMLFeatures(htmlParsed, listUrl.get(i));
			page.computeHTMLFeatures();
			listPage.add(page);
		}
		if(phase.equals("train")) {
			findNameStatisticsFeatures(listPage);
		}
		makeInstances(listHtml, listLabel, listPage);
	}
	
	private DenseInstance buildStatInstance(ClassificationHTMLFeatures page, Instances instances, String label){
		DenseInstance instance = new DenseInstance(nameStatisticsFeatures.size());
		String name = "";
		for(int i = 0; i <  nameStatisticsFeatures.size() - 1; i++){
			name = nameStatisticsFeatures.get(i);
			instance.setValue(instances.attribute(name), page.getHtmlStatistics().getOrDefault(name, 0.0));
		}
		name = nameStatisticsFeatures.get(nameStatisticsFeatures.size() - 1);
		if(label != null){
			instance.setValue(instances.attribute(name), label);
		}else{
			instance.setValue(instances.attribute(name), ClassificationWekaModel.classes.get(0));
		}
		return instance;
	}

	private static Instance makeInstance(Instances format, String text, String label) {
		DenseInstance instance = new DenseInstance(2);
		instance.setValue(format.attribute("text"), text);
		if(label != null) {
			instance.setValue(format.attribute("@@class@@"), label);
		}
		instance.setDataset(format);
		return instance;
	}

	private static Instances makeStructuralInstance(String modelName, Map<String, Class> features, List<String> classes, int capacity) {
		ArrayList<Attribute> attributes = new ArrayList<>();
		features.forEach((key, value) -> {
			if(value.equals(String.class)){
				attributes.add(new Attribute(key, (List)null));
			}else{
				attributes.add(new Attribute(key));
			}
		});

		if(!features.containsKey("@@class@@")) {
			Attribute classAttribute = new Attribute("@@class@@", classes);
			attributes.add(classAttribute);
		}

		Instances instances = new Instances(modelName, attributes, capacity);
		instances.setClassIndex(attributes.size() - 1);
		return instances;
	}

}
