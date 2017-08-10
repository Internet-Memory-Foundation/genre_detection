package net.internetmemory.genreclassification;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import weka.attributeSelection.ChiSquaredAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


class ClassificationBuildData {
	
	private List<String> nameStatisticsFeatures;	
	private Instances statisticsData;
	private Instances urlData;
	private List<String> listLabel;


	private List<String> features = new ArrayList<>();

	public List<String> getFeatures() {
		return features;
	}

	private static List<String> classes;
	
	ClassificationBuildData(List<String> _classes, List<String> nameStatisticsFeatures) {
		classes = _classes;
		this.nameStatisticsFeatures = nameStatisticsFeatures;
	}

	ClassificationBuildData(List<String> _classes){
		classes = _classes;
		this.nameStatisticsFeatures = new ArrayList<>();
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
		int numRows = resultXGB.length;
		int numCols = resultXGB[0].length;
		float[] features = new float[numCols * numRows * 2];
		for(int i=0; i<numRows; i++){
			for(int j=0; j<numCols; j++){
				features[2*i*numCols+j] = resultXGB[i][j];
				features[(2*i+1)*numCols+j] = (float)resultLR.get(i)[j];
			}
		}
		DMatrix dmat = new DMatrix(features, numRows, numCols*2);
		float[] labels = new float[listLabel.size()];
		for(int i=0; i<listLabel.size(); i++){
			labels[i] = classes.indexOf(listLabel.get(i));
		}
		dmat.setLabel(labels);
		return dmat;
	}

	private void makeInstances(List<String> listHtml, List<String> listLabel, List<ClassificationHTMLFeatures> listPage) throws Exception {
		// html statistics features
		makeStatisticsInstances("statistics_data", listHtml.size());
		for(int i = 0; i < listHtml.size(); i++){
			statisticsData.add(buildStatInstance(listPage.get(i), statisticsData, listLabel.get(i)));
		}

		for(int i=0; i<statisticsData.numAttributes(); i++){
			features.add(statisticsData.attribute(i).name());
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
		String name;
		for(int i = 0; i <  nameStatisticsFeatures.size() - 1; i++){
			name = nameStatisticsFeatures.get(i);
			instance.setValue(instances.attribute(name), page.getHtmlStatistics().getOrDefault(name, 0.0));
		}
		name = nameStatisticsFeatures.get(nameStatisticsFeatures.size() - 1);
		if(label != null){
			instance.setValue(instances.attribute(name), label);
		}else{
			instance.setValue(instances.attribute(name), ClassificationWekaModel.otherClasses.get(0));
		}
		return instance;
	}

	private Instances featureSelection(Instances data, int nbAttribute) throws Exception{
		AttributeSelection attributeSelection = new AttributeSelection();
		Ranker ranker = new Ranker();
		ranker.setNumToSelect(nbAttribute);
		ChiSquaredAttributeEval chi2 = new ChiSquaredAttributeEval();
		attributeSelection.setEvaluator(chi2);
		attributeSelection.setSearch(ranker);
		attributeSelection.setInputFormat(data);
		return Filter.useFilter(data, attributeSelection);
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
